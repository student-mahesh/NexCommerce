# scratch/test_m16_final_integration.ps1
$ErrorActionPreference = "Continue"

Write-Output "========================================================================"
Write-Output " NEXCOMMERCE - M16.6 FINAL INTEGRATION AND VERIFICATION SUITE"
Write-Output "========================================================================"

# Universal HTTP helper function for Windows PowerShell 5.1 & PowerShell 7
function Invoke-EcomApi {
    param(
        [string]$Uri,
        [string]$Method = "GET",
        [string]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $params = @{
        Uri = $Uri
        Method = $Method
        UseBasicParsing = $true
        TimeoutSec = 30
    }
    if ($Headers.Count -gt 0) { $params.Headers = $Headers }
    if ($Body) {
        $params.Body = $Body
        $params.ContentType = "application/json"
    }

    try {
        $resp = Invoke-WebRequest @params
        $contentStr = if ($resp.Content -is [byte[]]) {
            [System.Text.Encoding]::UTF8.GetString($resp.Content)
        } else {
            [string]$resp.Content
        }
        $json = $null
        try { $json = $contentStr | ConvertFrom-Json } catch {}
        return [PSCustomObject]@{
            StatusCode = [int]$resp.StatusCode
            Content = $contentStr
            Json = $json
            Success = $true
        }
    } catch [System.Net.WebException] {
        $webResp = $_.Exception.Response
        if ($webResp) {
            $statusCode = [int]$webResp.StatusCode
            $reader = New-Object System.IO.StreamReader($webResp.GetResponseStream())
            $content = $reader.ReadToEnd()
            $json = $null
            try { $json = $content | ConvertFrom-Json } catch {}
            return [PSCustomObject]@{
                StatusCode = $statusCode
                Content = $content
                Json = $json
                Success = $false
            }
        } else {
            return [PSCustomObject]@{
                StatusCode = 0
                Content = $_.Exception.Message
                Json = $null
                Success = $false
            }
        }
    } catch {
        return [PSCustomObject]@{
            StatusCode = 0
            Content = $_.Exception.Message
            Json = $null
            Success = $false
        }
    }
}

# -----------------------------------------------------------------------------
# 1. Stop any existing background java microservices
# -----------------------------------------------------------------------------
Write-Output "Stopping any existing background Java microservices..."
Get-Process | Where-Object { $_.ProcessName -match "java" } | ForEach-Object {
    try {
        Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
    } catch {}
}
Start-Sleep -Seconds 5

# -----------------------------------------------------------------------------
# 2. Launch all microservices
# -----------------------------------------------------------------------------
Write-Output "Launching microservices in background..."
$java = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe"

$services = @(
    @{ Name = "auth-service"; Port = 8086; Jar = "D:\EventDrivenEcommerce\auth-service\target\auth-service-1.0.0-SNAPSHOT.jar" },
    @{ Name = "product-service"; Port = 8081; Jar = "D:\EventDrivenEcommerce\product-service\target\product-service-1.0.0-SNAPSHOT.jar" },
    @{ Name = "inventory-service"; Port = 8083; Jar = "D:\EventDrivenEcommerce\inventory-service\target\inventory-service-1.0.0-SNAPSHOT.jar" },
    @{ Name = "payment-service"; Port = 8084; Jar = "D:\EventDrivenEcommerce\payment-service\target\payment-service-1.0.0-SNAPSHOT.jar" },
    @{ Name = "order-service"; Port = 8082; Jar = "D:\EventDrivenEcommerce\order-service\target\order-service-1.0.0-SNAPSHOT.jar" },
    @{ Name = "notification-service"; Port = 8085; Jar = "D:\EventDrivenEcommerce\notification-service\target\notification-service-1.0.0-SNAPSHOT.jar" },
    @{ Name = "api-gateway"; Port = 8080; Jar = "D:\EventDrivenEcommerce\api-gateway\target\api-gateway-1.0.0-SNAPSHOT.jar" }
)

$processes = @()
foreach ($svc in $services) {
    $logOut = "D:\EventDrivenEcommerce\scratch\$($svc.Name).log"
    $logErr = "D:\EventDrivenEcommerce\scratch\$($svc.Name)_err.log"
    $proc = Start-Process -FilePath $java -ArgumentList "-Xms64m", "-Xmx200m", "-jar", $svc.Jar -RedirectStandardOutput $logOut -RedirectStandardError $logErr -PassThru
    $processes += $proc
    Write-Output "  - Started $($svc.Name) (PID $($proc.Id)) on port $($svc.Port)"
    Start-Sleep -Seconds 2
}

Write-Output ""
Write-Output "Waiting up to 120 seconds for all 7 microservices to initialize..."

$allReady = $false
for ($i = 1; $i -le 60; $i++) {
    Start-Sleep -Seconds 2
    $upList = @()
    foreach ($svc in $services) {
        $check = Invoke-EcomApi -Uri "http://localhost:$($svc.Port)/actuator/health"
        if ($check.StatusCode -eq 200 -and $check.Json.status -eq "UP") {
            $upList += $svc.Name
        }
    }
    Write-Output "  [$($i * 2)s / 120s] $($upList.Count)/$($services.Count) UP: $($upList -join ', ')"
    if ($upList.Count -eq $services.Count) {
        $allReady = $true
        Write-Output "  [ALL READY] All $($services.Count) microservices are UP and HEALTHY!"
        Start-Sleep -Seconds 3
        break
    }
}

if (-not $allReady) {
    Write-Output "WARNING: Not all services reported UP within timeout. Continuing to test..."
}

# -----------------------------------------------------------------------------
# 3. Execution of Test Scenarios
# -----------------------------------------------------------------------------
$passed = 0
$failed = 0

function Assert-Test($condition, $name, $detail, $rawContent = $null) {
    if ($condition) {
        $global:passed++
        Write-Output "  [PASS] $name ($detail)"
    } else {
        $global:failed++
        Write-Output "  [FAIL] $name ($detail)"
        if ($rawContent) {
            Write-Output "         Raw: $rawContent"
        }
    }
}

Write-Output ""
Write-Output ">>> Running Integration Test Scenarios..."

# -------------------------------------------------------------------------
# Test 1: Actuator Health Check via API Gateway
# -------------------------------------------------------------------------
$t1 = Invoke-EcomApi -Uri "http://localhost:8080/actuator/health"
Assert-Test ($t1.StatusCode -eq 200 -and $t1.Json.status -eq "UP") "Test 1: Actuator Health via Gateway" "Status code $($t1.StatusCode), status=$($t1.Json.status)"

# -------------------------------------------------------------------------
# Test 2: Customer Registration & Token Issuance
# -------------------------------------------------------------------------
$custEmail = "customer_" + [System.Guid]::NewGuid().ToString().Substring(0, 8) + "@test.com"
$regBody = @{
    name = "M16 Test Customer"
    email = $custEmail
    password = "Password123!"
} | ConvertTo-Json

$t2 = Invoke-EcomApi -Uri "http://localhost:8080/api/auth/register" -Method "POST" -Body $regBody
$custToken = if ($t2.Json.data) { $t2.Json.data.token } else { $null }
Assert-Test ($t2.StatusCode -eq 201 -and $custToken -ne $null) "Test 2: Customer Registration" "Status $($t2.StatusCode), email=$custEmail"

# -------------------------------------------------------------------------
# Test 3: Customer Profile via Authenticated JWT
# -------------------------------------------------------------------------
$t3 = Invoke-EcomApi -Uri "http://localhost:8080/api/auth/me" -Headers @{ Authorization = "Bearer $custToken" }
Assert-Test ($t3.StatusCode -eq 200 -and $t3.Json.data.email -eq $custEmail) "Test 3: Customer Profile" "Status $($t3.StatusCode), role=$($t3.Json.data.role)"

# -------------------------------------------------------------------------
# Test 4: Catalog Browsing & Redis Caching
# -------------------------------------------------------------------------
$t4 = Invoke-EcomApi -Uri "http://localhost:8080/api/products"
$prodCount = if ($t4.Json.data.content) { $t4.Json.data.content.Count } else { 0 }
Assert-Test ($t4.StatusCode -eq 200 -and $prodCount -gt 0) "Test 4: Catalog Browsing" "Status $($t4.StatusCode), products count=$prodCount"

# -------------------------------------------------------------------------
# Test 5: Customer Order Placement & Saga Initiation
# -------------------------------------------------------------------------
$orderBody = @{
    customerName = "M16 Test Customer"
    shippingAddress = "123 Integration Way"
    paymentSimulateMode = "SUCCESS"
    items = @(
        @{
            productId = 1
            quantity = 1
        }
    )
} | ConvertTo-Json -Depth 5

$t5 = Invoke-EcomApi -Uri "http://localhost:8080/api/orders" -Method "POST" -Body $orderBody -Headers @{ Authorization = "Bearer $custToken" }
$orderId = if ($t5.Json.data) { $t5.Json.data.id } else { $null }
Assert-Test ($t5.StatusCode -eq 201 -and $orderId -gt 0) "Test 5: Order Placement & Saga" "Status $($t5.StatusCode), orderId=$orderId"

# -------------------------------------------------------------------------
# Test 6: Customer Order History
# -------------------------------------------------------------------------
$t6 = Invoke-EcomApi -Uri "http://localhost:8080/api/orders/my-orders" -Headers @{ Authorization = "Bearer $custToken" }
$hasOrder = if ($t6.Json.data) { ($t6.Json.data | Where-Object { $_.id -eq $orderId }) -ne $null } else { $false }
Assert-Test ($t6.StatusCode -eq 200 -and $hasOrder) "Test 6: Customer Order History" "Status $($t6.StatusCode), found order #$orderId"

# -------------------------------------------------------------------------
# Test 7: Admin Login & Privileges
# -------------------------------------------------------------------------
$adminLoginBody = @{
    email = "admin@test.com"
    password = "AdminPass123!"
} | ConvertTo-Json

$t7 = Invoke-EcomApi -Uri "http://localhost:8080/api/auth/login" -Method "POST" -Body $adminLoginBody
$adminToken = if ($t7.Json.data) { $t7.Json.data.token } else { $null }
Assert-Test ($t7.StatusCode -eq 200 -and $t7.Json.data.role -eq "ROLE_ADMIN") "Test 7: Admin Login" "Status $($t7.StatusCode), role=$($t7.Json.data.role)"

# -------------------------------------------------------------------------
# Test 8: Admin Product Catalog Management (Create & Soft-Delete)
# -------------------------------------------------------------------------
$newProd = @{
    name = "M16 Verified Test Product"
    description = "Created during M16 integration test"
    price = 79.99
    sku = "M16-VERIF-" + [System.Guid]::NewGuid().ToString().Substring(0, 6)
    imageUrl = "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=600"
    category = "Electronics"
    stockQuantity = 20
} | ConvertTo-Json

$t8Create = Invoke-EcomApi -Uri "http://localhost:8080/api/products" -Method "POST" -Body $newProd -Headers @{ Authorization = "Bearer $adminToken" }
$createdProdId = if ($t8Create.Json.data) { $t8Create.Json.data.id } else { $null }

$t8Delete = if ($createdProdId) {
    Invoke-EcomApi -Uri "http://localhost:8080/api/products/$createdProdId" -Method "DELETE" -Headers @{ Authorization = "Bearer $adminToken" }
} else { $null }

Assert-Test ($t8Create.StatusCode -eq 201 -and $t8Delete.StatusCode -eq 200) "Test 8: Admin Product Management" "Created id=$createdProdId (201), soft-deleted (200)"

# -------------------------------------------------------------------------
# Test 9: Admin Cross-Customer Order Oversight
# -------------------------------------------------------------------------
$t9 = Invoke-EcomApi -Uri "http://localhost:8080/api/orders/admin" -Headers @{ Authorization = "Bearer $adminToken" }
$adminOrderCount = if ($t9.Json.data) { $t9.Json.data.Count } else { 0 }
Assert-Test ($t9.StatusCode -eq 200 -and $adminOrderCount -gt 0) "Test 9: Admin Order Oversight" "Status $($t9.StatusCode), total orders=$adminOrderCount"

# -------------------------------------------------------------------------
# Test 10: Admin Inventory Oversight & Stock Replenishment
# -------------------------------------------------------------------------
$t10Inv = Invoke-EcomApi -Uri "http://localhost:8080/api/inventory"
$item1Before = ($t10Inv.Json.data | Where-Object { $_.productId -eq 1 }).availableQuantity

$repBody = @{ quantity = 5 } | ConvertTo-Json
$t10Rep = Invoke-EcomApi -Uri "http://localhost:8080/api/inventory/1/replenish" -Method "POST" -Body $repBody -Headers @{ Authorization = "Bearer $adminToken" }
$item1After = if ($t10Rep.Json.data) { $t10Rep.Json.data.availableQuantity } else { -1 }
Assert-Test ($t10Rep.StatusCode -eq 200 -and $item1After -eq ($item1Before + 5)) "Test 10: Admin Stock Replenishment" "Status $($t10Rep.StatusCode), stock: $item1Before -> $item1After"

# -------------------------------------------------------------------------
# Test 11: Security Guardrail - 401 Unauthorized on Missing Token
# -------------------------------------------------------------------------
$t11 = Invoke-EcomApi -Uri "http://localhost:8080/api/orders/my-orders"
Assert-Test ($t11.StatusCode -eq 401 -and $t11.Json.status -eq 401) "Test 11: Security - 401 Unauthorized" "Missing token returned HTTP 401 with ErrorResponse"

# -------------------------------------------------------------------------
# Test 12: Security Guardrail - 403 Forbidden on Role Escalation
# -------------------------------------------------------------------------
$repAttemptBody = @{ quantity = 10 } | ConvertTo-Json
$t12 = Invoke-EcomApi -Uri "http://localhost:8080/api/inventory/1/replenish" -Method "POST" -Body $repAttemptBody -Headers @{ Authorization = "Bearer $custToken" }
Assert-Test ($t12.StatusCode -eq 403 -and $t12.Json.status -eq 403) "Test 12: Security - 403 Forbidden" "Customer token blocked with HTTP 403 ErrorResponse"

# -------------------------------------------------------------------------
# Test 13: Error Handling - 400 Bad Request with Structured Validation Map
# -------------------------------------------------------------------------
$invalidProd = @{
    name = ""
    description = "Invalid product"
    price = -10.00
    sku = ""
    imageUrl = "https://example.com/img.jpg"
    category = "Electronics"
    stockQuantity = -5
} | ConvertTo-Json

$t13 = Invoke-EcomApi -Uri "http://localhost:8080/api/products" -Method "POST" -Body $invalidProd -Headers @{ Authorization = "Bearer $adminToken" }
$hasFieldErrors = $t13.Json.errors -ne $null
Assert-Test ($t13.StatusCode -eq 400 -and $hasFieldErrors) "Test 13: Error Handling - 400 Validation" "Status 400 with field errors: $($t13.Json.errors | ConvertTo-Json -Compress)"

# -------------------------------------------------------------------------
# Test 14: Error Handling - 404 Not Found on Missing Resource
# -------------------------------------------------------------------------
$t14 = Invoke-EcomApi -Uri "http://localhost:8080/api/orders/999999" -Headers @{ Authorization = "Bearer $adminToken" }
Assert-Test ($t14.StatusCode -eq 404 -and $t14.Json.status -eq 404) "Test 14: Error Handling - 404 Not Found" "Non-existent order returned HTTP 404 ErrorResponse"

# -------------------------------------------------------------------------
# Test 15: Error Handling - 409 Conflict on Duplicate Email
# -------------------------------------------------------------------------
$dupRegBody = @{
    name = "Duplicate Customer"
    email = $custEmail
    password = "Password123!"
} | ConvertTo-Json

$t15 = Invoke-EcomApi -Uri "http://localhost:8080/api/auth/register" -Method "POST" -Body $dupRegBody
Assert-Test ($t15.StatusCode -eq 409 -and $t15.Json.status -eq 409) "Test 15: Error Handling - 409 Conflict" "Duplicate registration returned HTTP 409 ErrorResponse"

# -----------------------------------------------------------------------------
# 4. Clean up microservices
# -----------------------------------------------------------------------------
Write-Output ""
Write-Output "Stopping microservices..."
foreach ($p in $processes) {
    try {
        Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
    } catch {}
}

Write-Output ""
Write-Output "========================================================================"
Write-Output " M16.6 INTEGRATION TEST SUMMARY: $passed PASSED, $failed FAILED"
Write-Output "========================================================================"

if ($failed -gt 0) {
    exit 1
}
