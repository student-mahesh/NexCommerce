$tokens = node D:\EventDrivenEcommerce\scratch\generate_tokens.js | ConvertFrom-Json
$adminToken = $tokens.adminToken
$customerToken = $tokens.customerToken

Write-Host "=========================================================="
Write-Host "PRODUCT SERVICE DIRECT SECURITY & TRUST BOUNDARY AUDIT"
Write-Host "=========================================================="

# Check initial DB count
Write-Host "`n[DB BASELINE CHECK]"
$initialCount = (docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -e "SELECT count(*) FROM ecommerce_product.products;").Trim()
Write-Host "Initial product count in DB: $initialCount"

# Payload for product creation
$newProductJson = @'
{
  "name": "Audit Test Gaming Mouse",
  "description": "Security testing mouse for direct mutation check",
  "price": 49.99,
  "sku": "AUDIT-MS-99",
  "imageUrl": "http://example.com/audit-mouse.jpg",
  "category": "Electronics",
  "stockQuantity": 25
}
'@

# Test 1: Public GET
Write-Host "`n[TEST 1] Public GET /api/products without credentials"
try {
    $resp1 = Invoke-WebRequest -Uri "http://localhost:8081/api/products" -Method GET -UseBasicParsing
    Write-Host "Status: $($resp1.StatusCode) (Expected: 200)"
    Write-Host "Success: $(($resp1.Content | ConvertFrom-Json).success)"
} catch {
    Write-Host "FAILED: $_"
}

# Test 2: Direct POST with Spoofed Admin Header, NO JWT
Write-Host "`n[TEST 2] Direct POST /api/products with Spoofed X-User-Roles: ROLE_ADMIN, NO JWT"
try {
    $resp2 = Invoke-WebRequest -Uri "http://localhost:8081/api/products" -Method POST `
        -Headers @{ "X-User-Roles" = "ROLE_ADMIN"; "X-User-Id" = "999"; "X-User-Email" = "attacker@evil.com" } `
        -ContentType "application/json" -Body $newProductJson -UseBasicParsing
    Write-Host "VULNERABILITY DETECTED! Request succeeded with status: $($resp2.StatusCode)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    $streamReader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $responseBody = $streamReader.ReadToEnd()
    Write-Host "Status: $statusCode (Expected: 401)"
    Write-Host "Response: $responseBody"
}

$dbCountAfterTest2 = (docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -e "SELECT count(*) FROM ecommerce_product.products;").Trim()
Write-Host "Product count in DB after Test 2: $dbCountAfterTest2 (Unchanged: $($initialCount -eq $dbCountAfterTest2))"

# Test 3: Direct POST with Customer JWT and Spoofed X-User-Roles: ROLE_ADMIN
Write-Host "`n[TEST 3] Direct POST /api/products with Customer JWT + Spoofed X-User-Roles: ROLE_ADMIN"
try {
    $resp3 = Invoke-WebRequest -Uri "http://localhost:8081/api/products" -Method POST `
        -Headers @{ "Authorization" = "Bearer $customerToken"; "X-User-Roles" = "ROLE_ADMIN"; "X-User-Id" = "1" } `
        -ContentType "application/json" -Body $newProductJson -UseBasicParsing
    Write-Host "VULNERABILITY DETECTED! Request succeeded with status: $($resp3.StatusCode)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    $streamReader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $responseBody = $streamReader.ReadToEnd()
    Write-Host "Status: $statusCode (Expected: 403)"
    Write-Host "Response: $responseBody"
}

$dbCountAfterTest3 = (docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -e "SELECT count(*) FROM ecommerce_product.products;").Trim()
Write-Host "Product count in DB after Test 3: $dbCountAfterTest3 (Unchanged: $($initialCount -eq $dbCountAfterTest3))"

# Test 4: Direct POST with Tampered / Invalid JWT
Write-Host "`n[TEST 4] Direct POST /api/products with Tampered/Invalid JWT"
try {
    $resp4 = Invoke-WebRequest -Uri "http://localhost:8081/api/products" -Method POST `
        -Headers @{ "Authorization" = "Bearer invalid.tampered.signature"; "X-User-Roles" = "ROLE_ADMIN" } `
        -ContentType "application/json" -Body $newProductJson -UseBasicParsing
    Write-Host "VULNERABILITY DETECTED! Request succeeded with status: $($resp4.StatusCode)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    $streamReader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $responseBody = $streamReader.ReadToEnd()
    Write-Host "Status: $statusCode (Expected: 401)"
    Write-Host "Response: $responseBody"
}

# Test 5: Direct POST with Valid Admin JWT
Write-Host "`n[TEST 5] Direct POST /api/products with Valid Admin JWT"
$createdProductId = $null
try {
    $resp5 = Invoke-WebRequest -Uri "http://localhost:8081/api/products" -Method POST `
        -Headers @{ "Authorization" = "Bearer $adminToken" } `
        -ContentType "application/json" -Body $newProductJson -UseBasicParsing
    Write-Host "Status: $($resp5.StatusCode) (Expected: 201)"
    $createdObj = $resp5.Content | ConvertFrom-Json
    $createdProductId = $createdObj.data.id
    Write-Host "Created product ID: $createdProductId, Name: $($createdObj.data.name), SKU: $($createdObj.data.sku)"
} catch {
    Write-Host "FAILED to create with admin token: $_"
}

$dbCountAfterTest5 = (docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -e "SELECT count(*) FROM ecommerce_product.products;").Trim()
Write-Host "Product count in DB after Test 5: $dbCountAfterTest5 (Incremented: $([int]$dbCountAfterTest5 -eq ([int]$initialCount + 1)))"

if ($createdProductId) {
    # Test 6: Direct PUT with spoofed admin header without JWT
    Write-Host "`n[TEST 6] Direct PUT /api/products/$createdProductId with Spoofed Admin Header, NO JWT"
    $updateJson = @'
{
  "name": "Hacked Product Name",
  "description": "Attempted unauthorized update",
  "price": 1.99,
  "sku": "AUDIT-MS-99",
  "imageUrl": "http://example.com/hacked.jpg",
  "category": "Electronics",
  "stockQuantity": 1
}
'@
    try {
        $resp6 = Invoke-WebRequest -Uri "http://localhost:8081/api/products/$createdProductId" -Method PUT `
            -Headers @{ "X-User-Roles" = "ROLE_ADMIN" } `
            -ContentType "application/json" -Body $updateJson -UseBasicParsing
        Write-Host "VULNERABILITY DETECTED! PUT succeeded with status: $($resp6.StatusCode)"
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "Status: $statusCode (Expected: 401)"
    }

    # Verify DB unchanged
    $dbProdName = (docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -e "SELECT name FROM ecommerce_product.products WHERE id = $createdProductId;").Trim()
    Write-Host "Product name in DB: $dbProdName (Should NOT be 'Hacked Product Name')"

    # Test 7: Direct DELETE with spoofed admin header without JWT
    Write-Host "`n[TEST 7] Direct DELETE /api/products/$createdProductId with Spoofed Admin Header, NO JWT"
    try {
        $resp7 = Invoke-WebRequest -Uri "http://localhost:8081/api/products/$createdProductId" -Method DELETE `
            -Headers @{ "X-User-Roles" = "ROLE_ADMIN" } -UseBasicParsing
        Write-Host "VULNERABILITY DETECTED! DELETE succeeded with status: $($resp7.StatusCode)"
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "Status: $statusCode (Expected: 401)"
    }

    # Verify DB row still exists
    $dbCountAfterTest7 = (docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -e "SELECT count(*) FROM ecommerce_product.products;").Trim()
    Write-Host "Product count in DB after Test 7: $dbCountAfterTest7 (Row still exists: $($dbCountAfterTest5 -eq $dbCountAfterTest7))"

    # Test 8: Direct DELETE with Valid Admin JWT
    Write-Host "`n[TEST 8] Direct DELETE /api/products/$createdProductId with Valid Admin JWT"
    try {
        $resp8 = Invoke-WebRequest -Uri "http://localhost:8081/api/products/$createdProductId" -Method DELETE `
            -Headers @{ "Authorization" = "Bearer $adminToken" } -UseBasicParsing
        Write-Host "Status: $($resp8.StatusCode) (Expected: 200)"
    } catch {
        Write-Host "FAILED: $_"
    }

    $finalDbCount = (docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -N -e "SELECT count(*) FROM ecommerce_product.products;").Trim()
    Write-Host "Final product count in DB: $finalDbCount (Cleanly restored: $($initialCount -eq $finalDbCount))"
}
