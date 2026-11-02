# scratch/test_m10_scenario_b.ps1
# Dedicated Scenario B Verification: Failure & Stock Compensation with a fresh order
$ErrorActionPreference = "Continue"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "SCENARIO B VERIFICATION: FAILURE & STOCK COMPENSATION" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Check stock before
Write-Host "`n[1/5] Checking initial stock for Product 1..." -ForegroundColor Yellow
docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, product_id, available_quantity, reserved_quantity FROM ecommerce_inventory.inventory_items WHERE product_id = 1;"

# 1. Start Payment Service and Inventory Service
Write-Host "`n[2/5] Starting payment-service and inventory-service..." -ForegroundColor Yellow
$paymentJob = Start-Process -FilePath "java" -ArgumentList "-jar", "payment-service\target\payment-service-1.0.0-SNAPSHOT.jar" -PassThru -NoNewWindow
$paymentPid = $paymentJob.Id

$inventoryJob = Start-Process -FilePath "java" -ArgumentList "-jar", "inventory-service\target\inventory-service-1.0.0-SNAPSHOT.jar" -PassThru -NoNewWindow
$inventoryPid = $inventoryJob.Id

# Wait for services
for ($i = 1; $i -le 25; $i++) {
    Start-Sleep -Seconds 1
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:8084/api/payments/order/999999" -Method GET -ErrorAction SilentlyContinue
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode.value__ -eq 404) {
            break
        }
    }
}
Write-Host "Services are up!" -ForegroundColor Green

# 2. Reserve stock in Inventory Service using OrderCreatedEvent for fresh order 9202
$orderId = 9202
$eventId = [System.Guid]::NewGuid().ToString()
Write-Host "`n[3/5] Reserving 2 units for orderId=$orderId via order-created-events..." -ForegroundColor Yellow
$orderCreated = @{
    eventId = $eventId
    correlationId = $eventId
    eventType = "OrderCreatedEvent"
    orderId = $orderId
    customerId = 1
    customerEmail = "buyer@test.com"
    totalAmount = 199.98
    paymentSimulateMode = "FAILURE"
    items = @(
        @{
            productId = 1
            productName = "Apex Pro Keyboard"
            sku = "TECH-KB-01"
            price = 99.99
            quantity = 2
        }
    )
    timestamp = [System.DateTime]::UtcNow.ToString("o")
} | ConvertTo-Json -Compress

"$orderId`:$orderCreated" | docker exec -i ecom-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:29092 --topic order-created-events --property "parse.key=true" --property "key.separator=:"

# Wait for Inventory Service to reserve and emit InventoryReservedEvent,
# then Payment Service consumes InventoryReservedEvent, declines payment (FAILURE mode),
# emits PaymentFailedEvent, and Inventory Service consumes PaymentFailedEvent to release stock!
Write-Host "Waiting 6 seconds for the complete saga choreography..." -ForegroundColor Yellow
Start-Sleep -Seconds 6

# 3. Check Payment record
Write-Host "`n[4/5] Checking payment transaction in MySQL for orderId=$orderId..." -ForegroundColor Yellow
$dbPayment = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, order_id, amount, status, payment_simulate_mode, transaction_reference, failure_reason FROM ecommerce_payment.payment_transactions WHERE order_id = $orderId;"
Write-Host $dbPayment

# 4. Check Stock Reservation & Inventory Items
Write-Host "`n[5/5] Checking stock reservation and inventory items after compensation..." -ForegroundColor Yellow
docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, order_id, product_id, quantity, status FROM ecommerce_inventory.stock_reservations WHERE order_id = $orderId;"
docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, product_id, available_quantity, reserved_quantity FROM ecommerce_inventory.inventory_items WHERE product_id = 1;"

# Clean up
Stop-Process -Id $paymentPid -Force -ErrorAction SilentlyContinue
Stop-Process -Id $inventoryPid -Force -ErrorAction SilentlyContinue
Write-Host "`nProcesses stopped. Scenario B verification complete!" -ForegroundColor Green

