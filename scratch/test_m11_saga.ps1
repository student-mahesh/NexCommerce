# scratch/test_m11_saga.ps1
# Milestone M11: End-to-End Saga Choreography Verification
$ErrorActionPreference = "Continue"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "MILESTONE M11: FULL SAGA CHOREOGRAPHY VERIFICATION (A-E)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Start all three microservices
Write-Host "`n[1/7] Starting Order (8082), Inventory (8083), and Payment (8084) services..." -ForegroundColor Yellow

$orderJob = Start-Process -FilePath "java" -ArgumentList "-jar", "order-service\target\order-service-1.0.0-SNAPSHOT.jar" -PassThru -NoNewWindow
$orderPid = $orderJob.Id

$inventoryJob = Start-Process -FilePath "java" -ArgumentList "-jar", "inventory-service\target\inventory-service-1.0.0-SNAPSHOT.jar" -PassThru -NoNewWindow
$inventoryPid = $inventoryJob.Id

$paymentJob = Start-Process -FilePath "java" -ArgumentList "-jar", "payment-service\target\payment-service-1.0.0-SNAPSHOT.jar" -PassThru -NoNewWindow
$paymentPid = $paymentJob.Id

Write-Host "Launched PIDs: Order=$orderPid, Inventory=$inventoryPid, Payment=$paymentPid"

# Wait for all services to become responsive
$allReady = $false
for ($i = 1; $i -le 35; $i++) {
    Start-Sleep -Seconds 1
    $orderUp = $false
    $invUp = $false
    $payUp = $false

    try {
        $res = Invoke-RestMethod -Uri "http://localhost:8082/api/orders/999999" -Headers @{ "X-User-Id"="1"; "X-User-Roles"="ROLE_ADMIN" } -Method GET -ErrorAction SilentlyContinue
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode.value__ -eq 404) { $orderUp = $true }
    }

    try {
        $res = Invoke-RestMethod -Uri "http://localhost:8083/api/inventory/1" -Method GET -ErrorAction SilentlyContinue
        if ($res.success) { $invUp = $true }
    } catch {}

    try {
        $res = Invoke-RestMethod -Uri "http://localhost:8084/api/payments/order/999999" -Method GET -ErrorAction SilentlyContinue
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode.value__ -eq 404) { $payUp = $true }
    }

    if ($orderUp -and $invUp -and $payUp) {
        $allReady = $true
        break
    }
}

if (-not $allReady) {
    Write-Host "Services failed to initialize in time! OrderUp=$orderUp, InvUp=$invUp, PayUp=$payUp" -ForegroundColor Red
    Stop-Process -Id $orderPid, $inventoryPid, $paymentPid -Force -ErrorAction SilentlyContinue
    exit 1
}
Write-Host "All three services are live and ready!" -ForegroundColor Green

$headers = @{
    "Content-Type" = "application/json"
    "X-User-Id" = "1"
    "X-User-Email" = "customer@test.com"
    "X-User-Roles" = "ROLE_CUSTOMER"
}

# =========================================================================
# Scenario A: Happy Path (SUCCESS)
# =========================================================================
Write-Host "`n[2/7] SCENARIO A: Happy Path (Order -> Reserve -> Pay -> Confirm)..." -ForegroundColor Yellow
$orderReqA = @{
    customerName = "Alice Customer"
    shippingAddress = "100 Market St, San Francisco, CA"
    paymentSimulateMode = "SUCCESS"
    items = @(
        @{
            productId = 1
            productName = "Apex Pro Keyboard"
            productSku = "TECH-KB-01"
            unitPrice = 89.99
            quantity = 1
        }
    )
} | ConvertTo-Json

$createdA = Invoke-RestMethod -Uri "http://localhost:8082/api/orders" -Method POST -Headers $headers -Body $orderReqA
$orderIdA = $createdA.data.id
Write-Host "Order created: id=$orderIdA, status=$($createdA.data.status)"

# Wait for full choreography saga: OrderCreated -> InventoryReserved -> PaymentSucceeded -> Order CONFIRMED
Start-Sleep -Seconds 5

# Verify Order Service state
$orderAfterA = Invoke-RestMethod -Uri "http://localhost:8082/api/orders/$orderIdA" -Method GET -Headers $headers
Write-Host "Order status after saga: $($orderAfterA.data.status)"
if ($orderAfterA.data.status -eq "CONFIRMED") {
    Write-Host "PASS: Order transitioned to CONFIRMED!" -ForegroundColor Green
} else {
    Write-Host "FAIL: Expected CONFIRMED but got $($orderAfterA.data.status)" -ForegroundColor Red
}

# Verify MySQL in ecommerce_payment
$payRowA = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, order_id, amount, status, transaction_reference FROM ecommerce_payment.payment_transactions WHERE order_id = $orderIdA;"
Write-Host $payRowA

# =========================================================================
# Scenario B: Payment Failure & Compensation Rollback
# =========================================================================
Write-Host "`n[3/7] SCENARIO B: Payment Failure & Compensation Rollback..." -ForegroundColor Yellow

# Query stock before
$stockBeforeB = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT available_quantity, reserved_quantity FROM ecommerce_inventory.inventory_items WHERE product_id = 1;"
Write-Host "Stock before order:"
Write-Host $stockBeforeB

$orderReqB = @{
    customerName = "Bob Customer"
    shippingAddress = "200 Broadway, New York, NY"
    paymentSimulateMode = "FAILURE"
    items = @(
        @{
            productId = 1
            productName = "Apex Pro Keyboard"
            productSku = "TECH-KB-01"
            unitPrice = 89.99
            quantity = 2
        }
    )
} | ConvertTo-Json

$createdB = Invoke-RestMethod -Uri "http://localhost:8082/api/orders" -Method POST -Headers $headers -Body $orderReqB
$orderIdB = $createdB.data.id
Write-Host "Order created: id=$orderIdB, status=$($createdB.data.status), simulateMode=FAILURE"

# Wait for saga: OrderCreated -> InventoryReserved -> PaymentFailed -> Order CANCELLED & Inventory Released
Start-Sleep -Seconds 6

# Verify Order Service state
$orderAfterB = Invoke-RestMethod -Uri "http://localhost:8082/api/orders/$orderIdB" -Method GET -Headers $headers
Write-Host "Order status after saga: $($orderAfterB.data.status)"
Write-Host "Cancellation reason: $($orderAfterB.data.cancellationReason)"
if ($orderAfterB.data.status -eq "CANCELLED") {
    Write-Host "PASS: Order transitioned to CANCELLED!" -ForegroundColor Green
} else {
    Write-Host "FAIL: Expected CANCELLED but got $($orderAfterB.data.status)" -ForegroundColor Red
}

# Verify stock restored in Inventory
Write-Host "Stock AFTER compensation rollback:"
$stockAfterB = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT available_quantity, reserved_quantity FROM ecommerce_inventory.inventory_items WHERE product_id = 1;"
Write-Host $stockAfterB

# Verify stock reservation status
$resvRowB = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, order_id, product_id, quantity, status FROM ecommerce_inventory.stock_reservations WHERE order_id = $orderIdB;"
Write-Host $resvRowB

# =========================================================================
# Scenario C: Insufficient Inventory
# =========================================================================
Write-Host "`n[4/7] SCENARIO C: Insufficient Inventory..." -ForegroundColor Yellow

$orderReqC = @{
    customerName = "Charlie Customer"
    shippingAddress = "300 Pine St, Seattle, WA"
    paymentSimulateMode = "SUCCESS"
    items = @(
        @{
            productId = 1
            productName = "Apex Pro Keyboard"
            productSku = "TECH-KB-01"
            unitPrice = 89.99
            quantity = 99999
        }
    )
} | ConvertTo-Json

$createdC = Invoke-RestMethod -Uri "http://localhost:8082/api/orders" -Method POST -Headers $headers -Body $orderReqC
$orderIdC = $createdC.data.id
Write-Host "Order created requesting 99999 units: id=$orderIdC"

# Wait for Inventory Service rejection -> Order Service cancellation
Start-Sleep -Seconds 5

$orderAfterC = Invoke-RestMethod -Uri "http://localhost:8082/api/orders/$orderIdC" -Method GET -Headers $headers
Write-Host "Order status after saga: $($orderAfterC.data.status)"
Write-Host "Cancellation reason: $($orderAfterC.data.cancellationReason)"
if ($orderAfterC.data.status -eq "CANCELLED") {
    Write-Host "PASS: Order cancelled due to inventory rejection!" -ForegroundColor Green
} else {
    Write-Host "FAIL: Expected CANCELLED but got $($orderAfterC.data.status)" -ForegroundColor Red
}

# Verify no payment transaction created
$payCountC = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT COUNT(*) as cnt FROM ecommerce_payment.payment_transactions WHERE order_id = $orderIdC;"
Write-Host "Payment transaction count for rejected order (should be 0):"
Write-Host $payCountC

# =========================================================================
# Scenario D: Duplicate Outcome Event Idempotency
# =========================================================================
Write-Host "`n[5/7] SCENARIO D: Duplicate Event Idempotency..." -ForegroundColor Yellow

# Send duplicate PaymentSucceededEvent for Order A
$dupEventA = @{
    eventId = [System.Guid]::NewGuid().ToString()
    correlationId = "corr-dup"
    eventType = "PaymentSucceededEvent"
    orderId = $orderIdA
    paymentId = 999
    transactionReference = "TXN-DUP"
    amount = 89.99
    timestamp = [System.DateTime]::UtcNow.ToString("o")
} | ConvertTo-Json -Compress

Write-Host "Publishing duplicate PaymentSucceededEvent for confirmed order $orderIdA..."
"$orderIdA`:$dupEventA" | docker exec -i ecom-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:29092 --topic payment-succeeded-events --property "parse.key=true" --property "key.separator=:"

Start-Sleep -Seconds 3

# Verify status unchanged
$orderCheckD = Invoke-RestMethod -Uri "http://localhost:8082/api/orders/$orderIdA" -Method GET -Headers $headers
Write-Host "Order status after duplicate event: $($orderCheckD.data.status)"
if ($orderCheckD.data.status -eq "CONFIRMED") {
    Write-Host "PASS: Order status unchanged (CONFIRMED)!" -ForegroundColor Green
}

# Check processed events table in MySQL
$procEventsD = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, order_id, event_type, processed_at FROM ecommerce_order.order_processed_events WHERE order_id = $orderIdA;"
Write-Host "Processed events in database:"
Write-Host $procEventsD

# =========================================================================
# Scenario E: Late / Conflicting Events
# =========================================================================
Write-Host "`n[6/7] SCENARIO E: Late / Conflicting Event on Terminal State..." -ForegroundColor Yellow

# Send late PaymentSucceededEvent for Order B (which is CANCELLED)
$conflictEvent = @{
    eventId = [System.Guid]::NewGuid().ToString()
    correlationId = "corr-late"
    eventType = "PaymentSucceededEvent"
    orderId = $orderIdB
    paymentId = 888
    transactionReference = "TXN-LATE"
    amount = 179.98
    timestamp = [System.DateTime]::UtcNow.ToString("o")
} | ConvertTo-Json -Compress

Write-Host "Publishing late PaymentSucceededEvent for CANCELLED order $orderIdB..."
"$orderIdB`:$conflictEvent" | docker exec -i ecom-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:29092 --topic payment-succeeded-events --property "parse.key=true" --property "key.separator=:"

Start-Sleep -Seconds 3

# Verify order remains CANCELLED and was NOT resurrected
$orderCheckE = Invoke-RestMethod -Uri "http://localhost:8082/api/orders/$orderIdB" -Method GET -Headers $headers
Write-Host "Order status after late conflicting event: $($orderCheckE.data.status)"
if ($orderCheckE.data.status -eq "CANCELLED") {
    Write-Host "PASS: Order remained safely in terminal state CANCELLED!" -ForegroundColor Green
} else {
    Write-Host "FAIL: Order was improperly resurrected to $($orderCheckE.data.status)!" -ForegroundColor Red
}

# Clean up
Write-Host "`n[7/7] Stopping background microservices..." -ForegroundColor Yellow
Stop-Process -Id $orderPid, $inventoryPid, $paymentPid -Force -ErrorAction SilentlyContinue
Write-Host "All background processes stopped cleanly. M11 Verification Complete!" -ForegroundColor Green

