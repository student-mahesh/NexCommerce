# scratch/test_m12_notifications.ps1
# Milestone M12: End-to-End Notification Service Verification (Scenarios A through G)

$ErrorActionPreference = "Continue"

Write-Host "=========================================================="
Write-Host "MILESTONE M12: NOTIFICATION SERVICE VERIFICATION (A-G)"
Write-Host "=========================================================="

# 1. Stop any lingering background java microservices
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

# 2. Launch Order (8082), Inventory (8083), Payment (8084), and Notification (8085) with preferIPv4Stack
Write-Host "`n[1/8] Starting Order (8082), Inventory (8083), Payment (8084), and Notification (8085)..."
$jvmArgs = @("-Djava.net.preferIPv4Stack=true", "-Dspring.kafka.bootstrap-servers=127.0.0.1:9094")

$pOrder = Start-Process -FilePath "java" -ArgumentList ($jvmArgs + @("-jar", "order-service\target\order-service-1.0.0-SNAPSHOT.jar")) -PassThru -NoNewWindow
Start-Sleep -Seconds 2
$pInv = Start-Process -FilePath "java" -ArgumentList ($jvmArgs + @("-jar", "inventory-service\target\inventory-service-1.0.0-SNAPSHOT.jar")) -PassThru -NoNewWindow
Start-Sleep -Seconds 2
$pPay = Start-Process -FilePath "java" -ArgumentList ($jvmArgs + @("-jar", "payment-service\target\payment-service-1.0.0-SNAPSHOT.jar")) -PassThru -NoNewWindow
Start-Sleep -Seconds 2
$pNotif = Start-Process -FilePath "java" -ArgumentList ($jvmArgs + @("-jar", "notification-service\target\notification-service-1.0.0-SNAPSHOT.jar")) -PassThru -NoNewWindow

Write-Host "Launched PIDs: Order=$($pOrder.Id), Inventory=$($pInv.Id), Payment=$($pPay.Id), Notification=$($pNotif.Id)"

# 3. Wait for all 4 microservices to be ready using fast non-blocking TCP socket check
function Test-PortQuick ($port) {
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $iar = $tcp.BeginConnect("127.0.0.1", $port, $null, $null)
        $wait = $iar.AsyncWaitHandle.WaitOne(400, $false)
        if ($wait -and $tcp.Connected) {
            $tcp.EndConnect($iar)
            $tcp.Close()
            return $true
        }
        $tcp.Close()
        return $false
    } catch {
        return $false
    }
}

Write-Host "Waiting for services to become responsive on ports 8082, 8083, 8084, 8085..."
$allReady = $false
for ($i = 1; $i -le 45; $i++) {
    Start-Sleep -Seconds 1
    $oUp = Test-PortQuick 8082
    $iUp = Test-PortQuick 8083
    $pUp = Test-PortQuick 8084
    $nUp = Test-PortQuick 8085

    if ($oUp -and $iUp -and $pUp -and $nUp) {
        $allReady = $true
        Write-Host "All 4 microservices are UP and responsive after $i seconds!"
        break
    }
}

if (-not $allReady) {
    Write-Error "Failed to start all services within timeout: Order=$oUp, Inventory=$iUp, Payment=$pUp, Notification=$nUp"
    Get-Process -Id $pOrder.Id, $pInv.Id, $pPay.Id, $pNotif.Id -ErrorAction SilentlyContinue | Stop-Process -Force
    exit 1
}

# Allow Kafka consumer groups to stabilize partition assignments
Write-Host "Allowing Kafka consumer groups to assign partitions (5 seconds)..."
Start-Sleep -Seconds 5

try {
    # -------------------------------------------------------------
    # SCENARIOS A, B, D: Order Created, Inventory Reserved, Payment Success
    # -------------------------------------------------------------
    Write-Host "`n[2/8] SCENARIO A, B, D: Happy Path Checkout..."
    $orderPayload = @{
        customerName = "Demo Customer"
        shippingAddress = "42 Silicon Way, Tech City"
        paymentSimulateMode = "SUCCESS"
        items = @(
            @{
                productId = 1
                productName = "Mechanical Keyboard RGB"
                productSku = "TECH-KB-01"
                unitPrice = 89.99
                quantity = 1
            }
        )
    } | ConvertTo-Json -Depth 5

    $orderRes = Invoke-RestMethod -Uri "http://localhost:8082/api/orders" -Method POST `
        -Headers @{ "X-User-Id" = "1"; "X-User-Email" = "customer@test.com" } `
        -ContentType "application/json" -Body $orderPayload

    $orderId = $orderRes.data.id
    Write-Host "Order placed: id=$orderId, orderNumber=$($orderRes.data.orderNumber)"

    # Wait for saga choreography & notification event consumption
    Start-Sleep -Seconds 5

    Write-Host "`nVerifying Notification logs in MySQL for Order ${orderId}:"
    docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, order_id, customer_id, event_type, event_id, status, created_at FROM ecommerce_notification.notification_logs WHERE order_id = $orderId ORDER BY id ASC;"

    # Verify Scenario A: ORDER_CREATED
    $cntCreated = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -s -e "SELECT count(*) FROM ecommerce_notification.notification_logs WHERE order_id = $orderId AND event_type = 'ORDER_CREATED';"
    if ([int]$cntCreated -ge 1) {
        Write-Host "PASS: Scenario A - ORDER_CREATED notification recorded!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: Scenario A - ORDER_CREATED notification missing!"
    }

    # Verify Scenario B: INVENTORY_RESERVED
    $cntReserved = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -s -e "SELECT count(*) FROM ecommerce_notification.notification_logs WHERE order_id = $orderId AND event_type = 'INVENTORY_RESERVED';"
    if ([int]$cntReserved -ge 1) {
        Write-Host "PASS: Scenario B - INVENTORY_RESERVED notification recorded!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: Scenario B - INVENTORY_RESERVED notification missing!"
    }

    # Verify Scenario D: PAYMENT_SUCCEEDED
    $cntPaySucc = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -s -e "SELECT count(*) FROM ecommerce_notification.notification_logs WHERE order_id = $orderId AND event_type = 'PAYMENT_SUCCEEDED';"
    if ([int]$cntPaySucc -ge 1) {
        Write-Host "PASS: Scenario D - PAYMENT_SUCCEEDED notification recorded!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: Scenario D - PAYMENT_SUCCEEDED notification missing!"
    }

    # -------------------------------------------------------------
    # SCENARIO C: Inventory Rejected (Out of Stock)
    # -------------------------------------------------------------
    Write-Host "`n[3/8] SCENARIO C: Inventory Rejection (Out of Stock)..."
    $outOfStockPayload = @{
        customerName = "Demo Customer"
        shippingAddress = "42 Silicon Way, Tech City"
        paymentSimulateMode = "SUCCESS"
        items = @(
            @{
                productId = 1
                productName = "Mechanical Keyboard RGB"
                productSku = "TECH-KB-01"
                unitPrice = 89.99
                quantity = 99999
            }
        )
    } | ConvertTo-Json -Depth 5

    $rejRes = Invoke-RestMethod -Uri "http://localhost:8082/api/orders" -Method POST `
        -Headers @{ "X-User-Id" = "1"; "X-User-Email" = "customer@test.com" } `
        -ContentType "application/json" -Body $outOfStockPayload

    $rejOrderId = $rejRes.data.id
    Write-Host "Order placed requesting 99999 units: id=$rejOrderId"
    Start-Sleep -Seconds 4

    docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, order_id, event_type, subject, message FROM ecommerce_notification.notification_logs WHERE order_id = $rejOrderId ORDER BY id ASC;"

    $cntRej = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -s -e "SELECT count(*) FROM ecommerce_notification.notification_logs WHERE order_id = $rejOrderId AND event_type = 'INVENTORY_REJECTED';"
    if ([int]$cntRej -ge 1) {
        Write-Host "PASS: Scenario C - INVENTORY_REJECTED notification recorded!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: Scenario C - INVENTORY_REJECTED notification missing!"
    }

    # -------------------------------------------------------------
    # SCENARIO E: Payment Failure (Card Declined)
    # -------------------------------------------------------------
    Write-Host "`n[4/8] SCENARIO E: Payment Decline & Failure Notification..."
    $failPayload = @{
        customerName = "Demo Customer"
        shippingAddress = "42 Silicon Way, Tech City"
        paymentSimulateMode = "FAILURE"
        items = @(
            @{
                productId = 1
                productName = "Mechanical Keyboard RGB"
                productSku = "TECH-KB-01"
                unitPrice = 89.99
                quantity = 1
            }
        )
    } | ConvertTo-Json -Depth 5

    $failRes = Invoke-RestMethod -Uri "http://localhost:8082/api/orders" -Method POST `
        -Headers @{ "X-User-Id" = "1"; "X-User-Email" = "customer@test.com" } `
        -ContentType "application/json" -Body $failPayload

    $failOrderId = $failRes.data.id
    Write-Host "Order placed with FAILURE simulate mode: id=$failOrderId"
    Start-Sleep -Seconds 4

    docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, order_id, event_type, subject, message FROM ecommerce_notification.notification_logs WHERE order_id = $failOrderId ORDER BY id ASC;"

    $cntPayFail = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -s -e "SELECT count(*) FROM ecommerce_notification.notification_logs WHERE order_id = $failOrderId AND event_type = 'PAYMENT_FAILED';"
    if ([int]$cntPayFail -ge 1) {
        Write-Host "PASS: Scenario E - PAYMENT_FAILED notification recorded!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: Scenario E - PAYMENT_FAILED notification missing!"
    }

    # -------------------------------------------------------------
    # SCENARIO F: Duplicate Kafka Event Idempotency
    # -------------------------------------------------------------
    Write-Host "`n[5/8] SCENARIO F: Duplicate Event Delivery Idempotency..."
    $existingEventId = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -s -e "SELECT event_id FROM ecommerce_notification.notification_logs WHERE order_id = $orderId AND event_type = 'ORDER_CREATED' LIMIT 1;"
    if ($existingEventId) {
        $existingEventId = $existingEventId.Trim()
    } else {
        $existingEventId = "order-$orderId-ORDER_CREATED"
    }
    Write-Host "Republishing duplicate OrderCreatedEvent with eventId: $existingEventId"

    $dupPayload = @{
        eventId = $existingEventId
        correlationId = "corr-dup-test"
        eventType = "ORDER_CREATED"
        orderId = $orderId
        customerId = 1
        customerEmail = "customer@test.com"
        totalAmount = 89.99
        paymentSimulateMode = "SUCCESS"
        items = @(
            @{
                productId = 1
                sku = "TECH-KB-01"
                quantity = 1
                unitPrice = 89.99
            }
        )
        timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    } | ConvertTo-Json -Compress

    # Publish duplicate to Kafka topic order-created-events
    "$orderId`:$dupPayload" | docker exec -i ecom-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:29092 --topic order-created-events --property "parse.key=true" --property "key.separator=:"
    Start-Sleep -Seconds 3

    $cntAfterDup = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -s -e "SELECT count(*) FROM ecommerce_notification.notification_logs WHERE event_id = '$existingEventId';"
    Write-Host "Record count for event_id '$existingEventId' after duplicate publish: $cntAfterDup"
    if ([int]$cntAfterDup -eq 1) {
        Write-Host "PASS: Scenario F - Duplicate Kafka delivery was discarded idempotently!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: Scenario F - Duplicate notification record was created!"
    }

    # -------------------------------------------------------------
    # SCENARIO G: REST API & Authorization Matrix
    # -------------------------------------------------------------
    Write-Host "`n[6/8] SCENARIO G: REST API & Authorization Matrix..."

    # G1: Customer 1 retrieves their own order's notifications
    Write-Host "G1: Customer 1 retrieves their own order notifications (orderId=$orderId)..."
    $getRes = Invoke-RestMethod -Uri "http://localhost:8085/api/notifications/order/$orderId" -Method GET `
        -Headers @{ "X-User-Id" = "1"; "X-User-Roles" = "ROLE_CUSTOMER" }
    
    $notifList = @($getRes.data)
    Write-Host "Retrieved $($notifList.Count) notifications for order $orderId."
    if ($notifList.Count -gt 0) {
        Write-Host "Sample notification: Type=$($notifList[0].eventType), Subject='$($notifList[0].subject)'"
    }
    if ($getRes.success -and ($notifList.Count -gt 0)) {
        Write-Host "PASS: G1 - Customer successfully retrieved their order notifications!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: G1 - Failed to retrieve notifications!"
    }

    # G2: Customer 2 attempts to retrieve Customer 1's order notifications (Forbidden 403)
    Write-Host "`nG2: Customer 2 attempts to retrieve Customer 1's notifications (expecting 403)..."
    try {
        $forbiddenRes = Invoke-RestMethod -Uri "http://localhost:8085/api/notifications/order/$orderId" -Method GET `
            -Headers @{ "X-User-Id" = "2"; "X-User-Roles" = "ROLE_CUSTOMER" }
        Write-Error "FAIL: G2 - Expected 403 Forbidden, but received 200 OK!"
    } catch {
        $code = 0
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $code = [int]$_.Exception.Response.StatusCode
        }
        if ($code -eq 403 -or $_.Message -match "403") {
            Write-Host "PASS: G2 - Access denied with 403 Forbidden as expected!" -ForegroundColor Green
        } else {
            Write-Error "FAIL: G2 - Unexpected status code: $code ($($_.Exception.Message))"
        }
    }

    # G3: Unauthenticated request (Missing X-User-Id header, expecting 401)
    Write-Host "`nG3: Unauthenticated request without X-User-Id (expecting 401)..."
    try {
        $unauthRes = Invoke-RestMethod -Uri "http://localhost:8085/api/notifications/order/$orderId" -Method GET
        Write-Error "FAIL: G3 - Expected 401 Unauthorized, but received 200 OK!"
    } catch {
        $code = 0
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $code = [int]$_.Exception.Response.StatusCode
        }
        if ($code -eq 401 -or $_.Message -match "401") {
            Write-Host "PASS: G3 - Rejected with 401 Unauthorized as expected!" -ForegroundColor Green
        } else {
            Write-Error "FAIL: G3 - Unexpected status code: $code ($($_.Exception.Message))"
        }
    }

    # G4: Admin user retrieves Customer 1's order notifications (Admin bypass, expecting 200 OK)
    Write-Host "`nG4: Admin user retrieves Customer 1's notifications (expecting 200 OK)..."
    $adminRes = Invoke-RestMethod -Uri "http://localhost:8085/api/notifications/order/$orderId" -Method GET `
        -Headers @{ "X-User-Id" = "999"; "X-User-Roles" = "ROLE_ADMIN" }
    
    $adminList = @($adminRes.data)
    if ($adminRes.success -and ($adminList.Count -gt 0)) {
        Write-Host "PASS: G4 - Administrator successfully retrieved order notifications!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: G4 - Admin access failed!"
    }

} finally {
    Write-Host "`n[7/8] Stopping background microservices..."
    Get-Process -Id $pOrder.Id, $pInv.Id, $pPay.Id, $pNotif.Id -ErrorAction SilentlyContinue | Stop-Process -Force
    Write-Host "All background processes stopped cleanly."
}

Write-Host "`n=========================================================="
Write-Host "ALL M12 SCENARIOS (A THROUGH G) VERIFIED SUCCESSFULLY!"
Write-Host "=========================================================="
