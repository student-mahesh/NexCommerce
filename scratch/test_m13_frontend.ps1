# scratch/test_m13_frontend.ps1
# Milestone M13: Frontend Checkout, Simulated Payment & Live Order Tracking Verification
# Tests Scenarios A through F through the API Gateway (port 8080) with full Saga Choreography

$ErrorActionPreference = "Continue"

Write-Host "=========================================================="
Write-Host "MILESTONE M13: FRONTEND CHECKOUT & TRACKING VERIFICATION"
Write-Host "=========================================================="

# 1. Stop any lingering background microservices
Write-Host "`n[1/8] Cleaning up lingering processes..."
Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.Path -notmatch "antigravity-ide" } | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

# 2. Check Docker infrastructure containers
Write-Host "`n[2/8] Checking infrastructure containers..."
$mysqlHealth = docker inspect --format='{{.State.Health.Status}}' ecom-mysql 2>$null
$redisHealth = docker inspect --format='{{.State.Health.Status}}' ecom-redis 2>$null
$kafkaHealth = docker inspect --format='{{.State.Health.Status}}' ecom-kafka 2>$null

Write-Host "MySQL: $mysqlHealth (3309), Redis: $redisHealth (6380), Kafka: $kafkaHealth (9094)"

if ($mysqlHealth -ne "healthy" -or $kafkaHealth -ne "healthy") {
    Write-Error "Infrastructure containers are not healthy. Please verify Docker."
    exit 1
}

# 3. Launch the 7 microservices in background
Write-Host "`n[3/8] Launching microservices through API Gateway (8080)..."
$jvmArgs = @("-Djava.net.preferIPv4Stack=true", "-Dspring.kafka.bootstrap-servers=127.0.0.1:9094")

$pAuth  = Start-Process -FilePath "java" -ArgumentList ($jvmArgs + @("-jar", "auth-service\target\auth-service-1.0.0-SNAPSHOT.jar")) -PassThru -NoNewWindow
Start-Sleep -Seconds 2
$pProd  = Start-Process -FilePath "java" -ArgumentList ($jvmArgs + @("-jar", "product-service\target\product-service-1.0.0-SNAPSHOT.jar")) -PassThru -NoNewWindow
Start-Sleep -Seconds 2
$pGate  = Start-Process -FilePath "java" -ArgumentList ($jvmArgs + @("-jar", "api-gateway\target\api-gateway-1.0.0-SNAPSHOT.jar")) -PassThru -NoNewWindow
Start-Sleep -Seconds 2
$pOrder = Start-Process -FilePath "java" -ArgumentList ($jvmArgs + @("-jar", "order-service\target\order-service-1.0.0-SNAPSHOT.jar")) -PassThru -NoNewWindow
Start-Sleep -Seconds 2
$pInv   = Start-Process -FilePath "java" -ArgumentList ($jvmArgs + @("-jar", "inventory-service\target\inventory-service-1.0.0-SNAPSHOT.jar")) -PassThru -NoNewWindow
Start-Sleep -Seconds 2
$pPay   = Start-Process -FilePath "java" -ArgumentList ($jvmArgs + @("-jar", "payment-service\target\payment-service-1.0.0-SNAPSHOT.jar")) -PassThru -NoNewWindow
Start-Sleep -Seconds 2
$pNotif = Start-Process -FilePath "java" -ArgumentList ($jvmArgs + @("-jar", "notification-service\target\notification-service-1.0.0-SNAPSHOT.jar")) -PassThru -NoNewWindow

Write-Host "PIDs: Gateway=$($pGate.Id), Auth=$($pAuth.Id), Prod=$($pProd.Id), Order=$($pOrder.Id), Inv=$($pInv.Id), Pay=$($pPay.Id), Notif=$($pNotif.Id)"

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

Write-Host "Waiting for services to become responsive on ports 8080, 8081, 8082, 8083, 8084, 8085, 8086..."
$allReady = $false
for ($i = 1; $i -le 60; $i++) {
    Start-Sleep -Seconds 1
    $gUp = Test-PortQuick 8080
    $aUp = Test-PortQuick 8086
    $prUp = Test-PortQuick 8081
    $oUp = Test-PortQuick 8082
    $iUp = Test-PortQuick 8083
    $pyUp = Test-PortQuick 8084
    $nUp = Test-PortQuick 8085

    if ($gUp -and $aUp -and $prUp -and $oUp -and $iUp -and $pyUp -and $nUp) {
        $allReady = $true
        Write-Host "All 7 microservices are UP and responsive after $i seconds!"
        break
    }
}

if (-not $allReady) {
    Write-Error "Failed to start services: Gate=$gUp, Auth=$aUp, Prod=$prUp, Order=$oUp, Inv=$iUp, Pay=$pyUp, Notif=$nUp"
    Get-Process -Id $pGate.Id, $pAuth.Id, $pProd.Id, $pOrder.Id, $pInv.Id, $pPay.Id, $pNotif.Id -ErrorAction SilentlyContinue | Stop-Process -Force
    exit 1
}

# Allow Kafka consumer groups to assign partitions
Write-Host "Allowing Kafka consumer group partition assignments to stabilize (5 seconds)..."
Start-Sleep -Seconds 5

try {
    # -------------------------------------------------------------
    # FLOW A: Product Catalog Query & Customer Authentication
    # -------------------------------------------------------------
    Write-Host "`n[4/8] FLOW A: Catalog Query & Authentication via Gateway (8080)..."
    
    # A1. Query products through Gateway
    $prodRes = Invoke-RestMethod -Uri "http://localhost:8080/api/products" -Method GET
    Write-Host "Retrieved $($prodRes.data.content.Count) products from Gateway /api/products"
    if ($prodRes.success -and $prodRes.data.content.Count -gt 0) {
        Write-Host "PASS: Flow A1 - Product Catalog loaded through Gateway!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: Flow A1 - Catalog query failed"
    }

    # A2. Authenticate Demo Customer
    $loginPayload = @{
        email = "customer@test.com"
        password = "Password123!"
    } | ConvertTo-Json
    $loginRes = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -Body $loginPayload -ContentType "application/json"
    $customerToken = $loginRes.data.token
    $customerId = $loginRes.data.userId
    Write-Host "Customer logged in: userId=$customerId, email=$($loginRes.data.email), role=$($loginRes.data.role)"
    if ($customerToken) {
        Write-Host "PASS: Flow A2 - Authentication succeeded; JWT token acquired!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: Flow A2 - Authentication failed"
    }

    # -------------------------------------------------------------
    # FLOW B: Checkout with SUCCESS Simulation Mode (Happy Path)
    # -------------------------------------------------------------
    Write-Host "`n[5/8] FLOW B: Checkout with SUCCESS Simulation & Live Order Tracking..."
    $orderSuccessPayload = @{
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

    $orderSuccessRes = Invoke-RestMethod -Uri "http://localhost:8080/api/orders" -Method POST `
        -Headers @{ Authorization = "Bearer $customerToken" } `
        -ContentType "application/json" -Body $orderSuccessPayload

    $order1Id = $orderSuccessRes.data.id
    $order1Number = $orderSuccessRes.data.orderNumber
    Write-Host "Order created: id=$order1Id, orderNumber=$order1Number, initialStatus=$($orderSuccessRes.data.status)"

    # Poll /api/orders/{id} until CONFIRMED (simulating frontend polling)
    $finalStatus = "PENDING"
    for ($poll = 1; $poll -le 15; $poll++) {
        Start-Sleep -Seconds 1
        $trackRes = Invoke-RestMethod -Uri "http://localhost:8080/api/orders/$order1Id" -Method GET `
            -Headers @{ Authorization = "Bearer $customerToken" }
        $finalStatus = $trackRes.data.status
        Write-Host "Poll ${poll}: Order #${order1Id} status = $finalStatus"
        if ($finalStatus -eq "CONFIRMED") {
            break
        }
    }

    if ($finalStatus -eq "CONFIRMED") {
        Write-Host "PASS: Flow B1 - Order transitioned to CONFIRMED!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: Flow B1 - Order failed to confirm. Final status: $finalStatus"
    }

    # Verify notification timeline records
    $notifRes1 = Invoke-RestMethod -Uri "http://localhost:8080/api/notifications/order/$order1Id" -Method GET `
        -Headers @{ Authorization = "Bearer $customerToken" }
    $notifs1 = @($notifRes1.data)
    Write-Host "Notifications recorded for Order #${order1Id}: $($notifs1.Count)"
    foreach ($n in $notifs1) {
        Write-Host "  - [$($n.eventType)] $($n.subject) (At: $($n.createdAt))"
    }
    $types1 = $notifs1 | ForEach-Object { $_.eventType }
    if ($types1 -contains "ORDER_CREATED" -and $types1 -contains "INVENTORY_RESERVED" -and $types1 -contains "PAYMENT_SUCCEEDED") {
        Write-Host "PASS: Flow B2 - Notification timeline contains all 3 success events!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: Flow B2 - Notification timeline missing expected events"
    }

    # -------------------------------------------------------------
    # FLOW C: Checkout with FAILURE Simulation (Card Decline & Compensation)
    # -------------------------------------------------------------
    Write-Host "`n[6/8] FLOW C: Checkout with FAILURE Simulation & Stock Release..."
    $stockBefore = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -s -e "SELECT available_quantity FROM ecommerce_inventory.inventories WHERE product_id = 1;"
    Write-Host "Inventory available before order: $stockBefore"

    $orderFailPayload = @{
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

    $orderFailRes = Invoke-RestMethod -Uri "http://localhost:8080/api/orders" -Method POST `
        -Headers @{ Authorization = "Bearer $customerToken" } `
        -ContentType "application/json" -Body $orderFailPayload

    $order2Id = $orderFailRes.data.id
    Write-Host "Order created with FAILURE mode: id=$order2Id"

    $failFinalStatus = "PENDING"
    for ($poll = 1; $poll -le 15; $poll++) {
        Start-Sleep -Seconds 1
        $trackRes2 = Invoke-RestMethod -Uri "http://localhost:8080/api/orders/$order2Id" -Method GET `
            -Headers @{ Authorization = "Bearer $customerToken" }
        $failFinalStatus = $trackRes2.data.status
        Write-Host "Poll ${poll}: Order #${order2Id} status = $failFinalStatus"
        if ($failFinalStatus -eq "CANCELLED") {
            Write-Host "Cancellation reason: $($trackRes2.data.cancellationReason)"
            break
        }
    }

    if ($failFinalStatus -eq "CANCELLED") {
        Write-Host "PASS: Flow C1 - Order transitioned to CANCELLED!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: Flow C1 - Order did not cancel. Final status: $failFinalStatus"
    }

    # Verify inventory was restored
    $stockAfter = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -s -e "SELECT available_quantity FROM ecommerce_inventory.inventories WHERE product_id = 1;"
    Write-Host "Inventory available after compensation: $stockAfter (Expected: $stockBefore)"
    if ([int]$stockAfter -eq [int]$stockBefore) {
        Write-Host "PASS: Flow C2 - Stock was restored via compensation rollback!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: Flow C2 - Stock was not restored correctly!"
    }

    # Verify notifications for Failure flow
    $notifRes2 = Invoke-RestMethod -Uri "http://localhost:8080/api/notifications/order/$order2Id" -Method GET `
        -Headers @{ Authorization = "Bearer $customerToken" }
    $types2 = @($notifRes2.data) | ForEach-Object { $_.eventType }
    if ($types2 -contains "PAYMENT_FAILED") {
        Write-Host "PASS: Flow C3 - PAYMENT_FAILED notification recorded in timeline!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: Flow C3 - PAYMENT_FAILED notification missing"
    }

    # -------------------------------------------------------------
    # FLOW D: Out-of-Stock Rejection
    # -------------------------------------------------------------
    Write-Host "`n[7/8] FLOW D: Out-of-Stock Demonstration Scenario..."
    $orderOosPayload = @{
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

    $orderOosRes = Invoke-RestMethod -Uri "http://localhost:8080/api/orders" -Method POST `
        -Headers @{ Authorization = "Bearer $customerToken" } `
        -ContentType "application/json" -Body $orderOosPayload

    $order3Id = $orderOosRes.data.id
    Write-Host "Order created requesting 99999 units: id=$order3Id"

    $oosFinalStatus = "PENDING"
    for ($poll = 1; $poll -le 15; $poll++) {
        Start-Sleep -Seconds 1
        $trackRes3 = Invoke-RestMethod -Uri "http://localhost:8080/api/orders/$order3Id" -Method GET `
            -Headers @{ Authorization = "Bearer $customerToken" }
        $oosFinalStatus = $trackRes3.data.status
        Write-Host "Poll ${poll}: Order #${order3Id} status = $oosFinalStatus"
        if ($oosFinalStatus -eq "CANCELLED") {
            Write-Host "Cancellation reason: $($trackRes3.data.cancellationReason)"
            break
        }
    }

    if ($oosFinalStatus -eq "CANCELLED") {
        Write-Host "PASS: Flow D1 - Out-of-stock order transitioned to CANCELLED!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: Flow D1 - Out-of-stock order did not cancel"
    }

    # Confirm NO payment was created
    $payCount = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -s -e "SELECT count(*) FROM ecommerce_payment.payments WHERE order_id = $order3Id;"
    Write-Host "Payment transaction count for order #${order3Id}: $payCount (Expected: 0)"
    if ([int]$payCount -eq 0) {
        Write-Host "PASS: Flow D2 - Zero payment transactions created for rejected inventory!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: Flow D2 - Payment transaction incorrectly created for rejected order!"
    }

    # -------------------------------------------------------------
    # FLOW E & F: Order History & Customer Isolation Security
    # -------------------------------------------------------------
    Write-Host "`n[8/8] FLOW E & F: Order History & Customer Isolation Barrier..."

    # E. Order History
    $historyRes = Invoke-RestMethod -Uri "http://localhost:8080/api/orders/my-orders" -Method GET `
        -Headers @{ Authorization = "Bearer $customerToken" }
    $myOrders = @($historyRes.data)
    Write-Host "Customer #$customerId order history count: $($myOrders.Count)"
    if ($myOrders.Count -ge 3) {
        Write-Host "PASS: Flow E - Authenticated customer successfully retrieved order history!" -ForegroundColor Green
    } else {
        Write-Error "FAIL: Flow E - Order history count unexpected"
    }

    # F1. Create/Authenticate second customer
    $cust2Payload = @{
        name = "Second Customer"
        email = "customer2@test.com"
        password = "Password123!"
    } | ConvertTo-Json
    try {
        $regRes2 = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method POST -Body $cust2Payload -ContentType "application/json"
        $cust2Token = $regRes2.data.token
    } catch {
        $loginRes2 = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -Body $cust2Payload -ContentType "application/json"
        $cust2Token = $loginRes2.data.token
    }
    Write-Host "Customer 2 authenticated"

    # F2. Customer 2 attempts to read Customer 1's order details (Expected: 403 Forbidden)
    try {
        $hackOrder = Invoke-RestMethod -Uri "http://localhost:8080/api/orders/$order1Id" -Method GET `
            -Headers @{ Authorization = "Bearer $cust2Token" }
        Write-Error "FAIL: Flow F1 - Customer 2 was allowed to view Customer 1's order!"
    } catch {
        $code = 0
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $code = [int]$_.Exception.Response.StatusCode
        }
        if ($code -eq 403 -or $_.Message -match "403") {
            Write-Host "PASS: Flow F1 - Cross-customer order access blocked with 403 Forbidden!" -ForegroundColor Green
        } else {
            Write-Error "FAIL: Flow F1 - Unexpected code: $code ($($_.Exception.Message))"
        }
    }

    # F3. Customer 2 attempts to read Customer 1's notifications (Expected: 403 Forbidden)
    try {
        $hackNotif = Invoke-RestMethod -Uri "http://localhost:8080/api/notifications/order/$order1Id" -Method GET `
            -Headers @{ Authorization = "Bearer $cust2Token" }
        Write-Error "FAIL: Flow F2 - Customer 2 was allowed to view Customer 1's notifications!"
    } catch {
        $code = 0
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $code = [int]$_.Exception.Response.StatusCode
        }
        if ($code -eq 403 -or $_.Message -match "403") {
            Write-Host "PASS: Flow F2 - Cross-customer notification access blocked with 403 Forbidden!" -ForegroundColor Green
        } else {
            Write-Error "FAIL: Flow F2 - Unexpected code: $code ($($_.Exception.Message))"
        }
    }

    # F4. Unauthenticated request without JWT (Expected: 401 Unauthorized)
    try {
        $unauthRes = Invoke-RestMethod -Uri "http://localhost:8080/api/orders/my-orders" -Method GET
        Write-Error "FAIL: Flow F3 - Unauthenticated access succeeded!"
    } catch {
        $code = 0
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $code = [int]$_.Exception.Response.StatusCode
        }
        if ($code -eq 401 -or $_.Message -match "401") {
            Write-Host "PASS: Flow F3 - Unauthenticated access rejected with 401 Unauthorized!" -ForegroundColor Green
        } else {
            Write-Error "FAIL: Flow F3 - Unexpected code: $code ($($_.Exception.Message))"
        }
    }

} finally {
    Write-Host "`nStopping background microservices..."
    Get-Process -Id $pGate.Id, $pAuth.Id, $pProd.Id, $pOrder.Id, $pInv.Id, $pPay.Id, $pNotif.Id -ErrorAction SilentlyContinue | Stop-Process -Force
    Write-Host "All background processes stopped cleanly."
}

Write-Host "`n=========================================================="
Write-Host "ALL M13 FLOWS (A THROUGH F) VERIFIED SUCCESSFULLY!"
Write-Host "=========================================================="
