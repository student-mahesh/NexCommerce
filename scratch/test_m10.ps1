# scratch/test_m10.ps1
# Integration test for Milestone M10: Payment Service
$ErrorActionPreference = "Continue"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "MILESTONE M10: PAYMENT SERVICE LIVE INTEGRATION VERIFICATION" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Start Payment Service in background
Write-Host "`n[1/6] Starting payment-service on port 8084..." -ForegroundColor Yellow
$paymentJob = Start-Process -FilePath "java" -ArgumentList "-jar", "payment-service\target\payment-service-1.0.0-SNAPSHOT.jar" -PassThru -NoNewWindow
$paymentPid = $paymentJob.Id
Write-Host "payment-service launched with PID: $paymentPid"

# Also start inventory-service on port 8083 for Scenario B compensation
Write-Host "`n[2/6] Starting inventory-service on port 8083 for compensation..." -ForegroundColor Yellow
$inventoryJob = Start-Process -FilePath "java" -ArgumentList "-jar", "inventory-service\target\inventory-service-1.0.0-SNAPSHOT.jar" -PassThru -NoNewWindow
$inventoryPid = $inventoryJob.Id
Write-Host "inventory-service launched with PID: $inventoryPid"

# Wait for payment-service to become healthy
$ready = $false
for ($i = 1; $i -le 30; $i++) {
    Start-Sleep -Seconds 1
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:8084/api/payments/order/999999" -Method GET -ErrorAction SilentlyContinue
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode.value__ -eq 404) {
            $ready = $true
            break
        }
    }
}

if (-not $ready) {
    Write-Host "payment-service did not start in 30 seconds!" -ForegroundColor Red
    Stop-Process -Id $paymentPid -Force -ErrorAction SilentlyContinue
    Stop-Process -Id $inventoryPid -Force -ErrorAction SilentlyContinue
    exit 1
}
Write-Host "payment-service is ready on port 8084!" -ForegroundColor Green

# Scenario A: SUCCESS
Write-Host "`n[3/6] Scenario A: Publish InventoryReservedEvent (SUCCESS mode)..." -ForegroundColor Yellow
$orderIdA = 9101
$eventIdA = [System.Guid]::NewGuid().ToString()
$eventA = @{
    eventId = $eventIdA
    correlationId = $eventIdA
    eventType = "InventoryReservedEvent"
    orderId = $orderIdA
    customerId = 1
    customerEmail = "buyer@test.com"
    totalAmount = 149.99
    paymentSimulateMode = "SUCCESS"
    reservedItems = @(
        @{ productId = 1; quantity = 1 }
    )
    timestamp = [System.DateTime]::UtcNow.ToString("o")
} | ConvertTo-Json -Compress

# Send event to Kafka
Write-Host "Publishing to inventory-reserved-events for orderId=$orderIdA..."
"$orderIdA`:$eventA" | docker exec -i ecom-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:29092 --topic inventory-reserved-events --property "parse.key=true" --property "key.separator=:"

Start-Sleep -Seconds 3

# Check MySQL record in ecommerce_payment
Write-Host "Querying ecommerce_payment.payment_transactions for orderId=$orderIdA..."
$dbRowA = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, order_id, amount, status, payment_simulate_mode, transaction_reference, demo_notice FROM ecommerce_payment.payment_transactions WHERE order_id = $orderIdA;"
Write-Host $dbRowA

# Query REST API
$apiResA = Invoke-RestMethod -Uri "http://localhost:8084/api/payments/order/$orderIdA" -Method GET
Write-Host "REST API Response:"
$apiResA | ConvertTo-Json

# Scenario B: FAILURE & Stock Release Compensation
Write-Host "`n[4/6] Scenario B: Publish InventoryReservedEvent (FAILURE mode) & Stock Compensation..." -ForegroundColor Yellow
# Order 6001 has 3 units of Product 1 reserved
# Check stock before
Write-Host "Stock before payment failure:"
docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, product_id, available_quantity, reserved_quantity FROM ecommerce_inventory.inventory_items WHERE product_id = 1;"
docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, order_id, status FROM ecommerce_inventory.stock_reservations WHERE order_id = 6001;"

$orderIdB = 6001
$eventIdB = [System.Guid]::NewGuid().ToString()
$eventB = @{
    eventId = $eventIdB
    correlationId = $eventIdB
    eventType = "InventoryReservedEvent"
    orderId = $orderIdB
    customerId = 1
    customerEmail = "buyer@test.com"
    totalAmount = 299.99
    paymentSimulateMode = "FAILURE"
    reservedItems = @(
        @{ productId = 1; quantity = 3 }
    )
    timestamp = [System.DateTime]::UtcNow.ToString("o")
} | ConvertTo-Json -Compress

# Send event to Kafka
Write-Host "Publishing to inventory-reserved-events for orderId=$orderIdB (FAILURE)..."
"$orderIdB`:$eventB" | docker exec -i ecom-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:29092 --topic inventory-reserved-events --property "parse.key=true" --property "key.separator=:"

Start-Sleep -Seconds 4

# Check MySQL payment record
Write-Host "Querying ecommerce_payment.payment_transactions for orderId=$orderIdB..."
$dbRowB = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, order_id, amount, status, payment_simulate_mode, transaction_reference, failure_reason FROM ecommerce_payment.payment_transactions WHERE order_id = $orderIdB;"
Write-Host $dbRowB

# Check stock after payment failure compensation in inventory service
Write-Host "Stock AFTER PaymentFailedEvent compensation:"
docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, product_id, available_quantity, reserved_quantity FROM ecommerce_inventory.inventory_items WHERE product_id = 1;"
docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, order_id, status FROM ecommerce_inventory.stock_reservations WHERE order_id = 6001;"

# Scenario C: IDEMPOTENCY
Write-Host "`n[5/6] Scenario C: Duplicate InventoryReservedEvent for orderId=$orderIdA..." -ForegroundColor Yellow
Write-Host "Publishing DUPLICATE event for orderId=$orderIdA..."
"$orderIdA`:$eventA" | docker exec -i ecom-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:29092 --topic inventory-reserved-events --property "parse.key=true" --property "key.separator=:"

Start-Sleep -Seconds 2

# Verify count is still exactly 1
Write-Host "Checking count of payment_transactions for orderId=$orderIdA..."
$countA = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT COUNT(*) as cnt FROM ecommerce_payment.payment_transactions WHERE order_id = $orderIdA;"
Write-Host $countA

# Clean up processes
Write-Host "`n[6/6] Stopping test services..." -ForegroundColor Yellow
Stop-Process -Id $paymentPid -Force -ErrorAction SilentlyContinue
Stop-Process -Id $inventoryPid -Force -ErrorAction SilentlyContinue
Write-Host "Processes stopped. Verification complete!" -ForegroundColor Green

