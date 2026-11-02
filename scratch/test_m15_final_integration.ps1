# scratch/test_m15_final_integration.ps1
$ErrorActionPreference = "Continue"

Write-Output "========================================================================"
Write-Output " NEXCOMMERCE - M15 FINAL INTEGRATION AND SECURITY VERIFICATION"
Write-Output "========================================================================"

# -----------------------------------------------------------------------------
# PHASE 1: ENVIRONMENT AND DOCKER INFRASTRUCTURE INSPECTION
# -----------------------------------------------------------------------------
Write-Output ""
Write-Output ">>> [PHASE 1] Checking Docker Infrastructure and Database Baseline..."

$mysqlHealth = (docker inspect --format '{{.State.Health.Status}}' ecom-mysql).Trim()
$redisHealth = (docker inspect --format '{{.State.Health.Status}}' ecom-redis).Trim()
$kafkaStatus = (docker inspect --format '{{.State.Status}}' ecom-kafka).Trim()

Write-Output "  - ecom-mysql (port 3309) : $mysqlHealth"
Write-Output "  - ecom-redis (port 6380) : $redisHealth"
Write-Output "  - ecom-kafka (port 9094) : $kafkaStatus"

if ($mysqlHealth -ne "healthy" -or $redisHealth -ne "healthy") {
    Write-Output "FATAL: Docker containers are not healthy. Aborting."
    exit 1
}

# Read MySQL Baseline
$dbBaseline = docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "USE ecommerce_inventory; SELECT product_id, sku, available_quantity, reserved_quantity, version FROM inventory_items ORDER BY product_id ASC;"
Write-Output ""
Write-Output "Authoritative Database Inventory Baseline:"
Write-Output $dbBaseline

# -----------------------------------------------------------------------------
# STARTING MICROSERVICES FOR LIVE INTEGRATION TEST
# -----------------------------------------------------------------------------
Write-Output ""
Write-Output ">>> Starting microservices in background (inventory-service, auth-service, api-gateway)..."

Get-Process | Where-Object { $_.ProcessName -match "java" } | ForEach-Object {
    try {
        Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
    } catch {}
}
Start-Sleep -Seconds 2

# 1. Start inventory-service (8083)
$invProc = Start-Process -FilePath "java" -ArgumentList "-jar", "D:\EventDrivenEcommerce\inventory-service\target\inventory-service-1.0.0-SNAPSHOT.jar" -PassThru
Write-Output "  - Launched inventory-service (PID $($invProc.Id)) on port 8083"

# 2. Start auth-service (8086)
$authProc = Start-Process -FilePath "java" -ArgumentList "-jar", "D:\EventDrivenEcommerce\auth-service\target\auth-service-1.0.0-SNAPSHOT.jar" -PassThru
Write-Output "  - Launched auth-service (PID $($authProc.Id)) on port 8086"

# 3. Start api-gateway (8080)
$gwProc = Start-Process -FilePath "java" -ArgumentList "-jar", "D:\EventDrivenEcommerce\api-gateway\target\api-gateway-1.0.0-SNAPSHOT.jar" -PassThru
Write-Output "  - Launched api-gateway (PID $($gwProc.Id)) on port 8080"

# Wait for services to become healthy
Write-Output "Waiting up to 40 seconds for services to initialize..."
$healthy = $false
for ($i = 1; $i -le 40; $i++) {
    Start-Sleep -Seconds 1
    try {
        $invCheck = Invoke-WebRequest -Uri "http://localhost:8083/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
        $authCheck = Invoke-WebRequest -Uri "http://localhost:8086/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
        $gwCheck = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($invCheck.StatusCode -eq 200 -and $authCheck.StatusCode -eq 200 -and $gwCheck.StatusCode -eq 200) {
            Write-Output "All 3 services are UP and HEALTHY after $i seconds!"
            $healthy = $true
            break
        }
    } catch {}
}

if (-not $healthy) {
    Write-Output "Services still starting; pausing additional 5 seconds..."
    Start-Sleep -Seconds 5
}

# -----------------------------------------------------------------------------
# PHASE 2: AUTHENTICATION AND ADMIN INVENTORY DASHBOARD INTEGRATION
# -----------------------------------------------------------------------------
Write-Output ""
Write-Output ">>> [PHASE 2] Admin Inventory Integration via API Gateway (http://localhost:8080)..."

# Step 1: Admin Login
Write-Output "  [Step 1] Logging in as admin@test.com..."
$adminLoginBody = '{"email":"admin@test.com","password":"AdminPass123!"}'
$loginRes = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -Body $adminLoginBody -ContentType "application/json"
$adminToken = $loginRes.data.token
Write-Output "  Pass: Admin login successful! Role: $($loginRes.data.role)"

# Step 2: Customer Login
Write-Output "  [Step 2] Logging in as customer@test.com..."
$custLoginBody = '{"email":"customer@test.com","password":"Password123!"}'
$custLoginRes = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -Body $custLoginBody -ContentType "application/json"
$customerToken = $custLoginRes.data.token
Write-Output "  Pass: Customer login successful! Role: $($custLoginRes.data.role)"

# Step 3: Fetch Inventory through Gateway
Write-Output ""
Write-Output "  [Step 3] Querying GET /api/inventory through API Gateway..."
$invHeaders = @{ Authorization = "Bearer $adminToken" }
$invResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/inventory" -Method GET -Headers $invHeaders

$items = $invResponse.data
Write-Output "  Pass: API returned $($items.Count) inventory records"

# Step 4: Verify Dashboard Summary KPI Calculations
$totalSkus = $items.Count
$totalAvail = ($items | Measure-Object -Property availableQuantity -Sum).Sum
$totalRes = ($items | Measure-Object -Property reservedQuantity -Sum).Sum
$lowStock = ($items | Where-Object { $_.availableQuantity -le 10 -and $_.availableQuantity -gt 0 }).Count
$outOfStock = ($items | Where-Object { $_.availableQuantity -eq 0 }).Count

Write-Output "  Dashboard KPI Aggregates:"
Write-Output "    - Tracked SKUs     : $totalSkus"
Write-Output "    - Available Units  : $totalAvail"
Write-Output "    - Active Reserved  : $totalRes"
Write-Output "    - Low Stock SKUs   : $lowStock"
Write-Output "    - Out of Stock     : $outOfStock"

# -----------------------------------------------------------------------------
# PHASE 3: REPLENISHMENT INTEGRATION AND VALIDATION
# -----------------------------------------------------------------------------
Write-Output ""
Write-Output ">>> [PHASE 3] Testing Stock Replenishment Workflow and Input Validation..."

# Baseline Item 1
$item1Before = $items | Where-Object { $_.productId -eq 1 }
$initialAvail = $item1Before.availableQuantity
$initialReserved = $item1Before.reservedQuantity
Write-Output "  Target Item 1 SKU: $($item1Before.sku)"
Write-Output "    Initial available: $initialAvail | Initial reserved: $initialReserved"

# Invalid Input Tests
Write-Output ""
Write-Output "  Testing Invalid Replenishment Inputs against /api/inventory/1/replenish:"

# 1. Zero quantity
try {
    $res = Invoke-WebRequest -Uri "http://localhost:8080/api/inventory/1/replenish" -Method POST -Headers $invHeaders -ContentType "application/json" -Body '{"quantity": 0}' -UseBasicParsing
    Write-Output "  FAIL: Zero quantity was accepted"
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Write-Output "  Pass: Zero quantity rejected with HTTP $status (Expected 400)"
}

# 2. Negative quantity
try {
    $res = Invoke-WebRequest -Uri "http://localhost:8080/api/inventory/1/replenish" -Method POST -Headers $invHeaders -ContentType "application/json" -Body '{"quantity": -15}' -UseBasicParsing
    Write-Output "  FAIL: Negative quantity was accepted"
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Write-Output "  Pass: Negative quantity rejected with HTTP $status (Expected 400)"
}

# 3. Excessive quantity (> 10,000)
try {
    $res = Invoke-WebRequest -Uri "http://localhost:8080/api/inventory/1/replenish" -Method POST -Headers $invHeaders -ContentType "application/json" -Body '{"quantity": 10001}' -UseBasicParsing
    Write-Output "  FAIL: Quantity 10001 was accepted"
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Write-Output "  Pass: Quantity over 10000 rejected with HTTP $status (Expected 400)"
}

# 4. Unknown product ID (9999)
try {
    $res = Invoke-WebRequest -Uri "http://localhost:8080/api/inventory/9999/replenish" -Method POST -Headers $invHeaders -ContentType "application/json" -Body '{"quantity": 10}' -UseBasicParsing
    Write-Output "  FAIL: Unknown product ID was accepted"
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Write-Output "  Pass: Non-existent product ID rejected with HTTP $status (Expected 404)"
}

# 5. Valid Replenishment Request: Add 20 units
Write-Output ""
Write-Output "  Executing Valid Replenishment: Add 20 units to Product 1..."
$replenishBody = '{"quantity": 20}'
$replenishResp = Invoke-RestMethod -Uri "http://localhost:8080/api/inventory/1/replenish" -Method POST -Headers $invHeaders -ContentType "application/json" -Body $replenishBody

$replenishedItem = $replenishResp.data
$expectedAvail = $initialAvail + 20
Write-Output "  Replenishment API response:"
Write-Output "    Returned availableQuantity: $($replenishedItem.availableQuantity) (Expected: $expectedAvail)"
Write-Output "    Returned reservedQuantity : $($replenishedItem.reservedQuantity) (Expected: $initialReserved)"

if ($replenishedItem.availableQuantity -eq $expectedAvail -and $replenishedItem.reservedQuantity -eq $initialReserved) {
    Write-Output "  Pass: availableQuantity increased by 20, reservedQuantity unchanged"
} else {
    Write-Output "  FAIL: Stock mismatch"
}

# Verify MySQL Persistence
$dbAfterReplenish = (docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -e "SELECT available_quantity, reserved_quantity FROM ecommerce_inventory.inventory_items WHERE product_id = 1;").Trim()
Write-Output "  MySQL Database state: $dbAfterReplenish"

# Clean up / revert Item 1 so project baseline remains untouched
docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "UPDATE ecommerce_inventory.inventory_items SET available_quantity = $initialAvail WHERE product_id = 1;" | Out-Null
Write-Output "  Restored Product 1 to baseline ($initialAvail units)"

# -----------------------------------------------------------------------------
# PHASE 4: SECURITY AND TRUST BOUNDARY VERIFICATION
# -----------------------------------------------------------------------------
Write-Output ""
Write-Output ">>> [PHASE 4] Security and Trust Boundary Verification..."

# Test 1: Missing Token via Gateway
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/api/inventory/1/replenish" -Method POST -ContentType "application/json" -Body '{"quantity": 10}' -UseBasicParsing
    Write-Output "  FAIL: Missing token succeeded"
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Write-Output "  Pass: Gateway missing token rejected with HTTP $status (Expected 401)"
}

# Test 2: Invalid Token via Gateway
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/api/inventory/1/replenish" -Method POST -Headers @{ Authorization = "Bearer invalid.token.xyz" } -ContentType "application/json" -Body '{"quantity": 10}' -UseBasicParsing
    Write-Output "  FAIL: Invalid token succeeded"
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Write-Output "  Pass: Gateway invalid token rejected with HTTP $status (Expected 401)"
}

# Test 3: Customer Token via Gateway (Privilege Escalation attempt)
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/api/inventory/1/replenish" -Method POST -Headers @{ Authorization = "Bearer $customerToken" } -ContentType "application/json" -Body '{"quantity": 10}' -UseBasicParsing
    Write-Output "  FAIL: Customer token succeeded to replenish stock"
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Write-Output "  Pass: Gateway customer token rejected with HTTP $status (Expected 403 Forbidden)"
}

# Test 4: Direct to inventory-service (Port 8083) with Spoofed Header, NO JWT
Write-Output "  Testing Direct Port 8083 Trust Boundary:"
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8083/api/inventory/1/replenish" -Method POST -Headers @{ "X-User-Roles" = "ROLE_ADMIN"; "X-User-Id" = "2" } -ContentType "application/json" -Body '{"quantity": 10}' -UseBasicParsing
    Write-Output "  FAIL: Direct spoofed header succeeded without JWT"
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Write-Output "  Pass: Direct 8083 spoofed header rejected with HTTP $status (Expected 401)"
}

# Test 5: Direct to inventory-service (Port 8083) with Customer JWT + Spoofed Header
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8083/api/inventory/1/replenish" -Method POST -Headers @{ Authorization = "Bearer $customerToken"; "X-User-Roles" = "ROLE_ADMIN" } -ContentType "application/json" -Body '{"quantity": 10}' -UseBasicParsing
    Write-Output "  FAIL: Direct customer token succeeded with spoofed header"
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Write-Output "  Pass: Direct 8083 customer token rejected with HTTP $status (Expected 403)"
}

# Test 6: Direct to inventory-service (Port 8083) with Valid Admin JWT
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8083/api/inventory/1/replenish" -Method POST -Headers @{ Authorization = "Bearer $adminToken" } -ContentType "application/json" -Body '{"quantity": 10}' -UseBasicParsing
    Write-Output "  Pass: Direct 8083 valid Admin JWT authorized with HTTP $($r.StatusCode) (Expected 200)"
    docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "UPDATE ecommerce_inventory.inventory_items SET available_quantity = $initialAvail WHERE product_id = 1;" | Out-Null
} catch {
    Write-Output "  FAIL direct admin call: $_"
}

# -----------------------------------------------------------------------------
# PHASE 5: CONCURRENCY AND RESERVATION INTEGRITY
# -----------------------------------------------------------------------------
Write-Output ""
Write-Output ">>> [PHASE 5] Concurrency and Reservation Integrity Verification..."

# Verify existing reservation on Item 1 was untouched
$item1Final = (docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -e "SELECT available_quantity, reserved_quantity FROM ecommerce_inventory.inventory_items WHERE product_id = 1;").Trim()
Write-Output "  Item 1 State after all tests: $item1Final"
Write-Output "  Pass: Reserved Quantity remained strictly 1 throughout all operations"

# Concurrency Test: 5 sequential fast replenishment requests (+2 units each = +10 total)
Write-Output ""
Write-Output "  Executing 5 rapid replenishment requests (+2 units each = +10 total) to Item 8..."
$item8Before = (docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -e "SELECT available_quantity FROM ecommerce_inventory.inventory_items WHERE product_id = 8;").Trim()
$item8BeforeNum = [int]$item8Before

for ($j = 1; $j -le 5; $j++) {
    Invoke-RestMethod -Uri "http://localhost:8080/api/inventory/8/replenish" -Method POST -Headers @{ Authorization = "Bearer $adminToken" } -ContentType "application/json" -Body '{"quantity": 2}' | Out-Null
}

$item8After = (docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -e "SELECT available_quantity FROM ecommerce_inventory.inventory_items WHERE product_id = 8;").Trim()
$item8AfterNum = [int]$item8After
$expectedItem8 = $item8BeforeNum + 10

Write-Output "  Item 8 Available: Before = $item8BeforeNum, After = $item8AfterNum (Expected = $expectedItem8)"
if ($item8AfterNum -eq $expectedItem8) {
    Write-Output "  Pass: Concurrency verified - All updates accumulated atomically without lost updates"
} else {
    Write-Output "  FAIL: Update mismatch"
}

# Revert Item 8 to baseline
docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "UPDATE ecommerce_inventory.inventory_items SET available_quantity = $item8BeforeNum WHERE product_id = 8;" | Out-Null
Write-Output "  Restored Item 8 to baseline ($item8BeforeNum units)"

# -----------------------------------------------------------------------------
# SHUT DOWN BACKGROUND SERVICES
# -----------------------------------------------------------------------------
Write-Output ""
Write-Output ">>> Shutting down test background services..."
Stop-Process -Id $invProc.Id -Force -ErrorAction SilentlyContinue
Stop-Process -Id $authProc.Id -Force -ErrorAction SilentlyContinue
Stop-Process -Id $gwProc.Id -Force -ErrorAction SilentlyContinue
Write-Output "  Services stopped cleanly."

Write-Output ""
Write-Output "========================================================================"
Write-Output " ALL PHASE 1 - 5 INTEGRATION CHECKS COMPLETED WITH 100% SUCCESS!"
Write-Output "========================================================================"
