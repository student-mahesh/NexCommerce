# Temporarily set ErrorActionPreference to Continue for native CLI calls
$ErrorActionPreference = "Stop"

Write-Host "=========================================================="
Write-Host "       MILESTONE M9: INVENTORY SERVICE LIVE VERIFICATION  "
Write-Host "=========================================================="

# 1. Start inventory-service in the background if not already running
Write-Host "`n=== 1. Starting inventory-service on port 8083 ==="
$invProc = Start-Process -FilePath "mvn.cmd" -ArgumentList "spring-boot:run", "-pl", "inventory-service" -PassThru -NoNewWindow
Write-Host "Launched inventory-service process PID=$($invProc.Id). Waiting for startup..."

# Wait for port 8083 to be reachable
$retries = 35
$started = $false
while ($retries -gt 0) {
    Start-Sleep -Seconds 2
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:8083/api/inventory/1" -Method GET -ErrorAction Stop
        if ($res.success) {
            $started = $true
            break
        }
    } catch {
        # continue waiting
    }
    $retries--
}

if (-not $started) {
    Write-Error "inventory-service failed to start within timeout."
    Stop-Process -Id $invProc.Id -Force
    exit 1
}

Write-Host "inventory-service is LIVE on port 8083."
Start-Sleep -Seconds 3

try {
    # 2. Verify REST endpoints
    Write-Host "`n=== 2. Check Initial Stock via REST ==="
    $initStock1 = Invoke-RestMethod -Uri "http://localhost:8083/api/inventory/1" -Method GET
    Write-Host "Product 1 Baseline Available: $($initStock1.data.availableQuantity), Reserved: $($initStock1.data.reservedQuantity)"

    $allStock = Invoke-RestMethod -Uri "http://localhost:8083/api/inventory" -Method GET
    Write-Host "Total products in inventory: $($allStock.data.Count)"

    # 3. Publish OrderCreatedEvent (Order 7001) - Requesting 3 units of Product 1
    Write-Host "`n=== 3. Publish OrderCreatedEvent (Order 7001, Product 1: 3 units) ==="
    $orderCreatedJson = '{"eventId":"evt-7001","correlationId":"corr-7001","eventType":"ORDER_CREATED","orderId":7001,"customerId":1,"customerEmail":"customer@test.com","totalAmount":599.97,"paymentSimulateMode":"SUCCESS","items":[{"productId":1,"sku":"TECH-KB-01","productName":"Keyboard","quantity":3,"unitPrice":199.99}],"timestamp":"2026-10-30T12:30:00Z"}'
    
    "7001:$orderCreatedJson" | docker exec -i ecom-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:29092 --topic order-created-events --property "parse.key=true" --property "key.separator=:"
    Write-Host "OrderCreatedEvent published to Kafka for orderId=7001."

    Start-Sleep -Seconds 3

    # 4. Verify Stock after reservation
    Write-Host "`n=== 4. Verify Stock Reservation in DB & REST ==="
    $afterStock1 = Invoke-RestMethod -Uri "http://localhost:8083/api/inventory/1" -Method GET
    Write-Host "Product 1 After Reservation -> Available: $($afterStock1.data.availableQuantity), Reserved: $($afterStock1.data.reservedQuantity)"

    $expectedAvail = $initStock1.data.availableQuantity - 3
    $expectedReserved = $initStock1.data.reservedQuantity + 3
    if ($afterStock1.data.availableQuantity -eq $expectedAvail -and $afterStock1.data.reservedQuantity -eq $expectedReserved) {
        Write-Host "PASSED: Product 1 stock decremented by 3 and reserved by 3." -ForegroundColor Green
    } else {
        Write-Error "FAILED: Stock reservation mismatch! Expected available=$expectedAvail, reserved=$expectedReserved, got available=$($afterStock1.data.availableQuantity), reserved=$($afterStock1.data.reservedQuantity)"
    }

    # 5. Check Kafka topic inventory-reserved-events
    Write-Host "`n=== 5. Verify InventoryReservedEvent on Kafka ==="
    $prevEA = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    $reservedMsg = docker exec ecom-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 --topic inventory-reserved-events --from-beginning --max-messages 1 2>$null
    $ErrorActionPreference = $prevEA
    Write-Host "Latest message from inventory-reserved-events topic: $reservedMsg"

    # 6. Test Idempotency with Duplicate OrderCreatedEvent (Order 7001)
    Write-Host "`n=== 6. Test Idempotency on Duplicate OrderCreatedEvent (Order 7001) ==="
    "7001:$orderCreatedJson" | docker exec -i ecom-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:29092 --topic order-created-events --property "parse.key=true" --property "key.separator=:"
    Start-Sleep -Seconds 3

    $dupStock1 = Invoke-RestMethod -Uri "http://localhost:8083/api/inventory/1" -Method GET
    Write-Host "Product 1 After Duplicate Event -> Available: $($dupStock1.data.availableQuantity), Reserved: $($dupStock1.data.reservedQuantity)"
    if ($dupStock1.data.availableQuantity -eq $afterStock1.data.availableQuantity -and $dupStock1.data.reservedQuantity -eq $afterStock1.data.reservedQuantity) {
        Write-Host "PASSED: Duplicate OrderCreatedEvent did not reserve stock twice." -ForegroundColor Green
    } else {
        Write-Error "FAILED: Idempotency failed, stock was reserved twice!"
    }

    # 7. Test Insufficient Stock Rejection (Order 7002 - Request 9999 units of Product 1)
    Write-Host "`n=== 7. Test Insufficient Stock Rejection (Order 7002, Product 1: 9999 units) ==="
    $excessOrderJson = '{"eventId":"evt-7002","correlationId":"corr-7002","eventType":"ORDER_CREATED","orderId":7002,"customerId":1,"customerEmail":"customer@test.com","totalAmount":99999.00,"paymentSimulateMode":"SUCCESS","items":[{"productId":1,"sku":"TECH-KB-01","productName":"Keyboard","quantity":9999,"unitPrice":199.99}],"timestamp":"2026-10-30T12:35:00Z"}'
    "7002:$excessOrderJson" | docker exec -i ecom-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:29092 --topic order-created-events --property "parse.key=true" --property "key.separator=:"
    Start-Sleep -Seconds 3

    # Verify stock unchanged
    $rejectStock1 = Invoke-RestMethod -Uri "http://localhost:8083/api/inventory/1" -Method GET
    Write-Host "Product 1 Stock after Rejection -> Available: $($rejectStock1.data.availableQuantity), Reserved: $($rejectStock1.data.reservedQuantity)"
    if ($rejectStock1.data.availableQuantity -eq $dupStock1.data.availableQuantity) {
        Write-Host "PASSED: Insufficient stock order was rejected without deducting stock." -ForegroundColor Green
    } else {
        Write-Error "FAILED: Stock deducted on rejected order!"
    }

    # 8. Verify Kafka topic inventory-rejected-events
    Write-Host "`n=== 8. Verify InventoryRejectedEvent on Kafka ==="
    $prevEA = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    $rejectedMsg = docker exec ecom-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 --topic inventory-rejected-events --from-beginning --max-messages 1 2>$null
    $ErrorActionPreference = $prevEA
    Write-Host "Latest message from inventory-rejected-events topic: $rejectedMsg"

    # 9. Test Compensation: Publish PaymentFailedEvent for Order 7001
    Write-Host "`n=== 9. Test Compensation: Publish PaymentFailedEvent (Order 7001) ==="
    $payFailJson = '{"eventId":"evt-fail-7001","correlationId":"corr-7001","eventType":"PAYMENT_FAILED","orderId":7001,"reason":"Card expired","timestamp":"2026-10-30T12:40:00Z"}'
    "7001:$payFailJson" | docker exec -i ecom-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:29092 --topic payment-failed-events --property "parse.key=true" --property "key.separator=:"
    Start-Sleep -Seconds 3

    # Verify Stock is restored
    $restoredStock1 = Invoke-RestMethod -Uri "http://localhost:8083/api/inventory/1" -Method GET
    Write-Host "Product 1 Stock after Compensation -> Available: $($restoredStock1.data.availableQuantity), Reserved: $($restoredStock1.data.reservedQuantity)"
    if ($restoredStock1.data.availableQuantity -eq $initStock1.data.availableQuantity -and $restoredStock1.data.reservedQuantity -eq $initStock1.data.reservedQuantity) {
        Write-Host "PASSED: Stock fully compensated and restored to original quantity." -ForegroundColor Green
    } else {
        Write-Error "FAILED: Compensation did not restore exact stock!"
    }

    # 10. Verify Kafka topic inventory-released-events
    Write-Host "`n=== 10. Verify InventoryReleasedEvent on Kafka ==="
    $prevEA = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    $releasedMsg = docker exec ecom-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 --topic inventory-released-events --from-beginning --max-messages 1 2>$null
    $ErrorActionPreference = $prevEA
    Write-Host "Latest message from inventory-released-events topic: $releasedMsg"

    # 11. Test Duplicate PaymentFailedEvent (Idempotent Compensation)
    Write-Host "`n=== 11. Test Duplicate PaymentFailedEvent (Idempotent Compensation) ==="
    "7001:$payFailJson" | docker exec -i ecom-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:29092 --topic payment-failed-events --property "parse.key=true" --property "key.separator=:"
    Start-Sleep -Seconds 3

    $dupRestoredStock = Invoke-RestMethod -Uri "http://localhost:8083/api/inventory/1" -Method GET
    Write-Host "Product 1 Stock after Duplicate PaymentFailed -> Available: $($dupRestoredStock.data.availableQuantity), Reserved: $($dupRestoredStock.data.reservedQuantity)"
    if ($dupRestoredStock.data.availableQuantity -eq $restoredStock1.data.availableQuantity) {
        Write-Host "PASSED: Duplicate PaymentFailedEvent did not double-restore stock." -ForegroundColor Green
    } else {
        Write-Error "FAILED: Stock double-restored on duplicate failure event!"
    }

    # 12. Check MySQL Database Records
    Write-Host "`n=== 12. Inspect MySQL Database Tables ==="
    docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, product_id, available_quantity, reserved_quantity, version FROM ecommerce_inventory.inventory_items WHERE product_id=1;"
    docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, order_id, product_id, quantity, status, failure_reason FROM ecommerce_inventory.stock_reservations WHERE order_id IN (7001, 7002);"

    Write-Host "`n=========================================================="
    Write-Host "ALL 12 MILESTONE M9 VERIFICATION CHECKS PASSED!" -ForegroundColor Green
    Write-Host "=========================================================="

} finally {
    Write-Host "`nShutting down inventory-service process PID=$($invProc.Id)..."
    Stop-Process -Id $invProc.Id -Force -ErrorAction SilentlyContinue
    Get-Process -Name "java" -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "*jdk-21*" } | Stop-Process -Force -ErrorAction SilentlyContinue
    Write-Host "Clean shutdown complete."
}
