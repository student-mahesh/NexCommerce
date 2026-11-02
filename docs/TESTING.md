# TESTING.md — Automated & Manual Verification Playbook

This document defines the exact commands and testing procedures to verify the Event-Driven E-Commerce Microservices platform.

---

## 1. Automated Unit & Integration Testing

Run all unit tests using Maven:
```powershell
# Run all tests across the entire repository
mvn clean test

# Run tests specifically for product-service
cd product-service
mvn test
```

---

## 2. Infrastructure Health Verification

Check container health status:
```powershell
docker compose ps
```
Expected Output:
```text
NAME         IMAGE                STATUS                  PORTS
ecom-mysql   mysql:8.4            Up (healthy)            0.0.0.0:3309->3306/tcp
ecom-redis   redis:8.0            Up (healthy)            0.0.0.0:6380->6379/tcp
ecom-kafka   apache/kafka:4.1.1   Up (healthy)            0.0.0.0:9094->9094/tcp
```

### Direct Port Connectivity Checks
```powershell
# Verify MySQL connection on port 3309
Test-NetConnection -ComputerName localhost -Port 3309

# Verify Redis connection on port 6380
Test-NetConnection -ComputerName localhost -Port 6380

# Verify Kafka broker on port 9094
Test-NetConnection -ComputerName localhost -Port 9094
```

---

## 3. Product Service API & Redis Cache Verification

### 3.1 Retrieve Product Catalog
```powershell
Invoke-RestMethod -Uri "http://localhost:8081/api/products" -Method GET
```
Verify:
- HTTP 200 OK
- Returns list of seed products (e.g., Mechanical Keyboard RGB, Wireless Headphones)
- Subsequent calls hit Redis cache (response time < 20ms).

### 3.2 Product Search & Filter
```powershell
# Search by keyword
Invoke-RestMethod -Uri "http://localhost:8081/api/products?search=Keyboard" -Method GET

# Filter by category
Invoke-RestMethod -Uri "http://localhost:8081/api/products?category=Electronics" -Method GET
```

### 3.3 Redis Cache Invalidation
```powershell
# 1. Fetch categories (populates Redis key 'categories::list')
Invoke-RestMethod -Uri "http://localhost:8081/api/categories" -Method GET

# 2. Add new product via POST
$newProduct = @{
    name = "Ultra HD 4K Monitor"
    description = "32-inch IPS gaming display with 144Hz refresh rate"
    price = 399.99
    sku = "TECH-MON-03"
    imageUrl = "https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=600&q=80"
    category = "Electronics"
    stockQuantity = 20
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8081/api/products" -Method POST -Body $newProduct -ContentType "application/json"

# 3. Verify new product appears in GET /api/products immediately
Invoke-RestMethod -Uri "http://localhost:8081/api/products" -Method GET
```

---

## 4. Auth Service & API Gateway Security Verification (Milestone M7)

```powershell
# Run M7 verification test suite
powershell -ExecutionPolicy Bypass -File C:\Users\Administrator\.gemini\antigravity\brain\c30c0f05-1512-4b0d-9800-08d0b41ee47d\scratch\test_m7.ps1
```

---

## 5. Order Service Verification (Milestone M8)

```powershell
# Run all order-service unit tests
mvn test -pl order-service

# Run M8 live end-to-end verification test suite
powershell -ExecutionPolicy Bypass -File C:\Users\Administrator\.gemini\antigravity\brain\c30c0f05-1512-4b0d-9800-08d0b41ee47d\scratch\test_m8.ps1

# Inspect MySQL database orders and items
docker exec ecom-mysql mysql -u root -prootpassword -e "SELECT * FROM ecommerce_order.orders; SELECT * FROM ecommerce_order.order_items;"

# Inspect Kafka topic order-created-events
docker exec ecom-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 --topic order-created-events --from-beginning --max-messages 1
```


---

## 6. Inventory Service Verification (Milestone M9)

```powershell
# 1. Run all inventory-service unit and controller tests (21 tests)
mvn test -pl inventory-service

# 2. Run M9 live end-to-end verification test suite
powershell -ExecutionPolicy Bypass -File scratch/test_m9.ps1

# 3. Inspect MySQL inventory and reservations
docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT * FROM ecommerce_inventory.inventory_items; SELECT * FROM ecommerce_inventory.stock_reservations;"

# 4. Inspect Kafka topics for inventory events
docker exec ecom-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 --topic inventory-reserved-events --from-beginning --max-messages 1
docker exec ecom-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 --topic inventory-rejected-events --from-beginning --max-messages 1
docker exec ecom-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 --topic inventory-released-events --from-beginning --max-messages 1
```

---

## 7. Payment Service Verification (Milestone M10)

```powershell
# 1. Run all payment-service unit and controller tests (22 tests)
mvn test -pl payment-service

# 2. Run M10 live end-to-end integration and idempotency verification test suite
powershell -ExecutionPolicy Bypass -File scratch/test_m10.ps1

# 3. Run Scenario B failure & stock compensation test suite
powershell -ExecutionPolicy Bypass -File scratch/test_m10_scenario_b.ps1

# 4. Inspect MySQL payment transactions
docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, order_id, amount, status, payment_simulate_mode, transaction_reference, failure_reason, demo_notice FROM ecommerce_payment.payment_transactions;"

# 5. Inspect Kafka topics for payment outcome events
docker exec ecom-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 --topic payment-succeeded-events --from-beginning --max-messages 1
docker exec ecom-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 --topic payment-failed-events --from-beginning --max-messages 1
```

---

## 8. Full Saga Choreography Verification (Milestone M11)

```powershell
# 1. Run all order-service unit tests (37 tests across controller, listeners, and saga logic)
mvn test -pl order-service

# 2. Run M11 end-to-end multi-service saga test suite (Scenarios A through E)
powershell -ExecutionPolicy Bypass -File scratch/test_m11_saga.ps1

# 3. Inspect order states and processed event deduplication records in MySQL
docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, order_number, status, total_amount, cancellation_reason FROM ecommerce_order.orders;"
docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT * FROM ecommerce_order.order_processed_events;"

# 4. Inspect inventory state to confirm permanent fulfillment or stock release
docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT product_id, available_quantity, reserved_quantity FROM ecommerce_inventory.inventory_items; SELECT order_id, status FROM ecommerce_inventory.stock_reservations;"
```

---

## 9. Notification Service Verification (Milestone M12)

```powershell
# 1. Run all notification-service unit and controller tests (24 tests)
mvn test -pl notification-service

# 2. Run M12 end-to-end multi-service notification test suite (Scenarios A through G)
powershell -ExecutionPolicy Bypass -File scratch/test_m12_notifications.ps1

# 3. Inspect notification audit logs in MySQL
docker exec ecom-mysql mysql -uecom_user -pecom_secure_pass -e "SELECT id, order_id, customer_id, event_type, event_id, status, created_at FROM ecommerce_notification.notification_logs ORDER BY id DESC;"

# 4. Test notification retrieval REST API via Gateway
# As Customer 1 (Own order -> 200 OK):
Invoke-RestMethod -Uri "http://localhost:8085/api/notifications/order/8" -Headers @{ "X-User-Id" = "1"; "X-User-Roles" = "ROLE_CUSTOMER" }

# As Customer 2 (Forbidden -> 403 Forbidden):
Invoke-RestMethod -Uri "http://localhost:8085/api/notifications/order/8" -Headers @{ "X-User-Id" = "2"; "X-User-Roles" = "ROLE_CUSTOMER" }

# As Administrator (Admin bypass -> 200 OK):
Invoke-RestMethod -Uri "http://localhost:8085/api/notifications/order/8" -Headers @{ "X-User-Id" = "999"; "X-User-Roles" = "ROLE_ADMIN" }
```


---

## 13. Milestone M13: Frontend Checkout, Payment Simulation & Saga Tracking Verification

### 13.1 Automated Cart Unit Tests (`npm test --prefix frontend`)
- **Execution Command**: `npm test --prefix frontend` (or `node frontend/test-cart-logic.js`)
- **Results**: 10 tests run, 0 failures, 0 errors.
  1. `addToCart` new product.
  2. `addToCart` existing product aggregates quantity.
  3. `addToCart` multiple distinct products.
  4. `selectCartTotal` calculates exact subtotal: `$319.96`.
  5. `selectCartCount` calculates total units: `4`.
  6. `updateQuantity` modifies item quantity.
  7. `updateQuantity` enforces stock limit clamping.
  8. `updateQuantity(0)` removes item from cart.
  9. `removeFromCart` deletes item.
  10. `clearCart` empties all items.

### 13.2 Frontend Production Build
- **Execution Command**: `npm run build --prefix frontend`
- **Results**: `✓ 2125 modules transformed. built in 23.76s` with zero errors.

### 13.3 End-to-End Live Integration Verification (`scratch/test_m13_frontend.ps1`)
- **Execution Command**: `powershell -ExecutionPolicy Bypass -File scratch/test_m13_frontend.ps1`
- **Results**:
  - **Flow A**: Catalog query (`/api/products`) and customer authentication (`/api/auth/login`) verified.
  - **Flow B**: Happy path checkout with `SUCCESS` mode transitions to `CONFIRMED`; timeline contains `ORDER_CREATED`, `INVENTORY_RESERVED`, and `PAYMENT_SUCCEEDED`.
  - **Flow C**: Checkout with `FAILURE` simulation transitions to `CANCELLED`; inventory compensation restores stock; `PAYMENT_FAILED` notification logged.
  - **Flow D**: Out-of-stock scenario (99,999 units) triggers `INVENTORY_REJECTED` and transitions to `CANCELLED` with zero payment records.
  - **Flow E**: Customer order history (`/api/orders/my-orders`) retrieves all customer orders.
  - **Flow F**: Cross-customer isolation verified with `403 Forbidden` on mismatched customer IDs and `401 Unauthorized` on missing tokens.

---

## 14. Milestone M14: Integration, Security & Reliability Audit Verification

### 14.1 Full Monorepo Maven Regression Test Suite
- **Execution Command**: `& 'D:\SpringBoot Files\apache-maven-3.10.0-rc-1\bin\mvn.cmd' test`
- **Results**: **165 tests run, 0 failures, 0 errors, 0 skipped** across all 8 modules (BUILD SUCCESS).
  - Parent POM: SUCCESS
  - `api-gateway`: 13 tests passed (Includes anti-spoofing header stripping tests)
  - `auth-service`: 20 tests passed (BCrypt, JWT minting/validation, user authentication, customer/admin authorization)
  - `product-service`: 19 tests passed (Catalog CRUD, Redis cache-aside)
  - `order-service`: 47 tests passed (Includes fail-closed authoritative catalog price validation, client price independence, strict active status rejection, 503 on catalog outage, 400 on not found, multi-item atomicity)
  - `inventory-service`: 21 tests passed (Pessimistic locking, reservation, compensation release)
  - `payment-service`: 20 tests passed (Deterministic MockPaymentGateway, idempotency protection)
  - `notification-service`: 25 tests passed (Audit timeline, DB failure ACK protection)

### 14.2 Security Defect Fixes & Empirical Verification
1. **Gateway Identity Header Spoofing (CVE Prevention)**:
   - File: `api-gateway/src/main/java/com/ecommerce/gateway/filter/JwtAuthFilter.java`
   - Fix: Added `httpHeaders.remove("X-User-Id")` etc. on public endpoints, and `httpHeaders.set(...)` (overwriting any client-supplied headers) on authenticated paths.
   - Verified by: `JwtAuthFilterTest.testFilter_StripsClientInjectedSpoofedHeadersOnPublicEndpoints` and `testFilter_OverwritesClientInjectedSpoofedHeadersWithJwtClaimsOnSecuredEndpoints`.
2. **Fail-Closed Authoritative Catalog Price Validation & Client Price Independence**:
   - Files:
     - `order-service/src/main/java/com/ecommerce/order/service/OrderService.java`
     - `order-service/src/main/java/com/ecommerce/order/client/ProductCatalogClient.java`
     - `order-service/src/main/java/com/ecommerce/order/exception/ProductServiceUnavailableException.java`
     - `order-service/src/main/java/com/ecommerce/order/exception/GlobalExceptionHandler.java`
     - `order-service/src/main/java/com/ecommerce/order/dto/CreateOrderItemRequest.java`
   - Implementation Details:
     - Client `unitPrice`, `productName`, and `productSku` are optional and never determine saved values. Only `productId` and `quantity` are required.
     - Product Catalog client queries `product-service` using `${product-service.url:http://${PRODUCT_SERVICE_HOST:localhost}:${PRODUCT_SERVICE_PORT:8081}}`.
     - Strict active status verification: rejects `active: false` as well as `null`/missing active status (`catalogProduct.active() == null || !catalogProduct.active()`).
     - Product Service outage/timeout throws `ProductServiceUnavailableException` which maps to HTTP `503 Service Unavailable` with `ApiResponse.error(...)`.
     - Missing product in catalog throws `IllegalArgumentException` which maps to HTTP `400 Bad Request`.
     - No database save or Kafka message publishing occurs if catalog validation fails.
     - In multi-item orders, every item is validated before any persistence or event emission occurs.
   - Verified by:
     - `OrderServiceTest.testCreateOrder_NullClientPriceAndMetadata_SucceedsUsingCatalog`
     - `OrderServiceTest.testCreateOrder_NegativeClientPrice_IgnoredInFavorOfCatalogPrice`
     - `OrderServiceTest.testCreateOrderOverridesTamperedPriceWithAuthoritativeCatalogPrice`
     - `OrderServiceTest.testCreateOrder_ProductServiceUnavailable`
     - `OrderServiceTest.testCreateOrder_ProductNotFound`
     - `OrderServiceTest.testCreateOrder_NullActiveStatus_Rejected`
     - `OrderServiceTest.testCreateOrder_InactiveProduct`
     - `OrderServiceTest.testCreateOrder_InvalidCatalogPrice`
     - `OrderServiceTest.testCreateOrder_MultipleProducts_AllMustValidate`
     - `OrderControllerTest.shouldReturn503WhenProductServiceUnavailable`
     - `OrderControllerTest.shouldReturn400WhenProductNotFound`

---

## 15. Milestone M15 Step 2: Critical Product Authorization Vulnerabilities Verification

### 15.1 Gateway Method-Aware Public Path Filtering
- **File**: `api-gateway/src/main/java/com/ecommerce/gateway/filter/JwtAuthFilter.java`
- **Behavior**:
  - `GET /api/products` and `GET /api/categories` bypass authentication as public read endpoints.
  - `POST /api/products`, `PUT /api/products/{id}`, and `DELETE /api/products/{id}` require valid Bearer JWT.
  - Missing or invalid tokens return `401 Unauthorized` with `ApiResponse.error(...)`.
  - Valid JWT injects claims (`X-User-Id`, `X-User-Email`, `X-User-Roles`) for downstream services.
- **Verified by**: `JwtAuthFilterTest` (19 tests passing).
  - `shouldBypassFilterForPublicProducts` (GET -> allowed)
  - `shouldBypassFilterForPublicSingleProduct` (GET -> allowed)
  - `shouldBypassFilterForPublicCategories` (GET -> allowed)
  - `shouldRejectPostProductsWithoutToken` (POST -> 401 Unauthorized)
  - `shouldRejectPutProductsWithoutToken` (PUT -> 401 Unauthorized)
  - `shouldRejectDeleteProductsWithoutToken` (DELETE -> 401 Unauthorized)
  - `shouldAllowAdminPostProductsWithToken` (POST with Admin JWT -> 200/injected headers)

### 15.2 Product Service Role-Based Authorization
- **File**: `product-service/src/main/java/com/ecommerce/product/controller/ProductController.java`
- **Behavior**:
  - `GET /api/products` and `GET /api/products/{id}` remain accessible to all callers without role check.
  - `POST /api/products`, `PUT /api/products/{id}`, and `DELETE /api/products/{id}` strictly check `X-User-Roles.contains("ROLE_ADMIN")`.
  - Non-admin callers (such as `ROLE_CUSTOMER` or missing headers) receive `403 Forbidden` with `"Access denied: Admin role required"`.
- **Verified by**: `ProductControllerTest` (11 tests passing) & `ProductServiceTest` (5 tests passing).
  - `shouldAllowPublicGetProducts` (200 OK)
  - `shouldAllowPublicGetProductById` (200 OK)
  - `shouldAllowCreateProductAsAdmin` (201 Created)
  - `shouldRejectCreateProductAsCustomer` (403 Forbidden)
  - `shouldRejectCreateProductWithoutRoleHeader` (403 Forbidden)
  - `shouldAllowUpdateProductAsAdmin` (200 OK)
  - `shouldRejectUpdateProductAsCustomer` (403 Forbidden)
  - `shouldRejectUpdateProductWithoutRoleHeader` (403 Forbidden)
  - `shouldAllowDeleteProductAsAdmin` (200 OK)
  - `shouldRejectDeleteProductAsCustomer` (403 Forbidden)
  - `shouldRejectDeleteProductWithoutRoleHeader` (403 Forbidden)

### 15.3 Monorepo Regression Test Results
- **Execution Command**: `& 'D:\SpringBoot Files\apache-maven-3.10.0-rc-1\bin\mvn.cmd' test`
- **Results**: **168 tests run, 0 failures, 0 errors, 0 skipped** across all 8 modules (BUILD SUCCESS).
  - `api-gateway`: 19 tests passed
  - `auth-service`: 20 tests passed
  - `product-service`: 16 tests passed
  - `order-service`: 47 tests passed
  - `inventory-service`: 21 tests passed
  - `payment-service`: 20 tests passed
  - `notification-service`: 25 tests passed
  - Frontend Cart Unit Tests: 10/10 passed (`npm test --prefix frontend`)

### 14.3 Reliability & Failure Audit
1. **Kafka Listener Manual Acknowledgment Under DB Failure**:
   - File: `notification-service/src/main/java/com/ecommerce/notification/kafka/NotificationEventListener.java`
   - Audit: `ack.acknowledge()` is called strictly after `notificationService.recordNotification(...)`. Database exceptions propagate uncaught to Spring Kafka, keeping partition offset uncommitted for redelivery. Poison pills (`JacksonException`) are safely caught and acknowledged to prevent consumer lag loops.
   - Verified by: `NotificationEventListenerTest.testOnOrderCreated_DatabaseFailure_DoesNotAcknowledge`.
2. **Frontend Test & Build**:
   - Frontend Unit Tests: `npm test --prefix frontend` (10/10 passed).
   - Frontend Production Build: `npm run build --prefix frontend` (2125 modules transformed, built in 10.96s, 0 errors).

---

## 16. Milestone M15 Step 2A: Product Service Trust Boundary & Zero-Trust Verification

### 16.1 Threat Model & Direct Access Audit
- **Port Mapping Inspection**:
  - Backing infrastructure containers: `ecom-mysql` (3309:3306), `ecom-redis` (6380:6379), `ecom-kafka` (9094:9094).
  - Business microservices (`product-service`, `order-service`, `auth-service`, `api-gateway`) run on the host network.
  - `product-service` listens on `0.0.0.0:8081`. Any client reachable on port 8081 can bypass `api-gateway` (port 8080).
- **Vulnerability Prior to Step 2A**:
  - `ProductController` checked `userRolesHeader != null && userRolesHeader.contains("ROLE_ADMIN")`.
  - Direct callers sending `X-User-Roles: ROLE_ADMIN` without a valid Bearer JWT could perform mutations directly against the database.
- **Zero-Trust Hardening Implemented**:
  - Created `com.ecommerce.product.security.JwtUtil` in `product-service`.
  - Added `verifyAdminAuthorization(authHeader, userRolesHeader)` in `ProductController`.
  - Requires a valid `Authorization: Bearer <token>` signed with HMAC-SHA256 using `${app.jwt.secret}`.
  - Requires exact role match `"ROLE_ADMIN".equals(tokenRole)`.

### 16.2 Live Direct HTTP Penetration Test Results (`scratch/test_product_trust_boundary.ps1`)
- **Initial MySQL Product Count**: 8
- **Test 1 — Public GET**:
  - Endpoint: `GET http://localhost:8081/api/products` without headers.
  - Result: HTTP `200 OK`. `{"success": true, "message": "Products retrieved successfully", ...}`.
- **Test 2 — Direct Spoofed Admin Header Without JWT**:
  - Endpoint: `POST http://localhost:8081/api/products` with `X-User-Roles: ROLE_ADMIN`, `X-User-Id: 999`, no JWT.
  - Result: HTTP `401 Unauthorized`. `{"success": false, "message": "Authentication required: Missing or invalid Authorization header"}`.
  - Database Product Count: **8 (Unchanged: True)**. Zero mutation occurred.
- **Test 3 — Direct Customer JWT + Spoofed Admin Header**:
  - Endpoint: `POST http://localhost:8081/api/products` with `Authorization: Bearer <Customer_JWT>` + `X-User-Roles: ROLE_ADMIN`.
  - Result: HTTP `403 Forbidden`. `{"success": false, "message": "Access denied: Admin role required"}`.
  - Database Product Count: **8 (Unchanged: True)**. Zero mutation occurred.
- **Test 4 — Direct Tampered / Invalid JWT**:
  - Endpoint: `POST http://localhost:8081/api/products` with `Authorization: Bearer invalid.tampered.token`.
  - Result: HTTP `401 Unauthorized`. `{"success": false, "message": "Expired or invalid JWT token"}`.
  - Database Product Count: **8 (Unchanged: True)**.
- **Test 5 — Direct Valid Admin JWT**:
  - Endpoint: `POST http://localhost:8081/api/products` with `Authorization: Bearer <Admin_JWT>`.
  - Result: HTTP `201 Created`. Created product ID: 9 (`Audit Test Gaming Mouse`, SKU `AUDIT-MS-99`).
  - Database Product Count: **9 (Incremented: True)**.
- **Test 6 — Direct PUT with Spoofed Admin Header Without JWT**:
  - Endpoint: `PUT http://localhost:8081/api/products/9` with `X-User-Roles: ROLE_ADMIN`, no JWT.
  - Result: HTTP `401 Unauthorized`. Database record name remained unchanged (`Audit Test Gaming Mouse`).
- **Test 7 — Direct DELETE with Spoofed Admin Header Without JWT**:
  - Endpoint: `DELETE http://localhost:8081/api/products/9` with `X-User-Roles: ROLE_ADMIN`, no JWT.
  - Result: HTTP `401 Unauthorized`. Database record preserved.
- **Test 8 — Direct DELETE with Valid Admin JWT**:
  - Endpoint: `DELETE http://localhost:8081/api/products/9` with `Authorization: Bearer <Admin_JWT>`.
  - Result: HTTP `200 OK`. Soft-deleted product 9 (`active = 0`), cleaned up afterwards to restore baseline 8 products.

### 16.3 Gateway Path-Matching and CORS/OPTIONS Audit
- **File**: `api-gateway/src/main/java/com/ecommerce/gateway/filter/JwtAuthFilter.java`
- **Path Matching Implementation**:
  - Prefix matching replaced with strict `isPathOrSubpath(path, base)` to prevent path traversal/unintended prefix bypasses.
  - `/api/products` and subpaths (`/api/products/1`, `/api/products/search`) allow GET without token.
  - `/api/categories` and subpaths (`/api/categories/5`) allow GET without token.
  - Unintended prefixes (e.g., `/api/products-secret`, `/api/categories-private`) require JWT (HTTP 401).
  - All mutating HTTP methods (`POST`, `PUT`, `PATCH`, `DELETE`) on `/api/products` require valid JWT (HTTP 401).
  - CORS preflight (`OPTIONS`) requests bypass authentication filter unconditionally (`request.getMethod() == HttpMethod.OPTIONS`).

### 16.4 Complete Monorepo Regression Test Results
- **Execution Command**: `& 'D:\SpringBoot Files\apache-maven-3.10.0-rc-1\bin\mvn.cmd' test`
- **Total Tests Run**: **178 tests, 0 failures, 0 errors, 0 skipped** across all 8 modules (BUILD SUCCESS).
  - `api-gateway`: 24 tests passed (20 `JwtAuthFilterTest` + 4 `JwtUtilTest`)
  - `auth-service`: 15 tests passed
  - `product-service`: 19 tests passed (14 `ProductControllerTest` + 5 `ProductServiceTest`)
  - `order-service`: 29 tests passed
  - `inventory-service`: 31 tests passed
  - `payment-service`: 26 tests passed
  - `notification-service`: 22 tests passed (8 `NotificationEventListenerTest` + 11 `NotificationServiceTest` + 3 unit tests)
  - `ecommerce-parent`: 1 validation passed
  - Frontend Cart Unit Tests: 10/10 passed (`npm test --prefix frontend`)

---

## 17. Milestone M15 Step 4: Admin Product Catalog Management UI Verification

### 17.1 Discovered Backend API Contract
- `GET /api/products?page={p}&size={s}&category={c}&search={q}`: Paginated active catalog items (`PageResponse<ProductResponse>`).
- `GET /api/categories`: Category list with active product counts (`List<CategoryResponse>`).
- `POST /api/products`: Admin product creation with body `ProductRequest` (`name`, `description`, `price`, `sku`, `category`, `stockQuantity`, `imageUrl`).
- `PUT /api/products/{id}`: Admin product modification with body `ProductRequest`.
- `DELETE /api/products/{id}`: Admin soft-deletion (`product.setActive(false)`), evicts Redis cache.

### 17.2 Frontend Automated Unit Tests (`npm test --prefix frontend`)
- **Execution Command**: `npm test --prefix frontend`
- **Total Tests Run**: **30 tests, 0 failures, 0 errors** (BUILD SUCCESS).
  - Cart Redux Logic (`test-cart-logic.js`): 10 tests passed.
  - Admin Auth & Route Protection (`test-admin-auth.js`): 10 tests passed.
  - Admin Product Management (`test-admin-products.js`): 10 tests passed:
    1. Form validation: Valid product request passes.
    2. Form validation: Rejects zero and negative price (`<= 0.00`).
    3. Form validation: Rejects negative stock quantity (`< 0`).
    4. Form validation: Rejects non-HTTP/HTTPS image URL protocols (prevents script/ftp injection).
    5. Form validation: Rejects missing and invalid SKU formats.
    6. Redux Reducer: Initial state inspection with admin action fields.
    7. Redux Reducer: `fetchProducts.fulfilled` updates catalog.
    8. Redux Reducer: `updateProduct.fulfilled` mutates product in place.
    9. Redux Reducer: `deleteProduct.fulfilled` filters out soft-deleted item from active catalog.
    10. Redux Reducer: `clearAdminFeedback` resets alert messages.

### 17.3 Frontend Production Build
- **Execution Command**: `npm run build --prefix frontend`
- **Results**: `✓ 2135 modules transformed. built in 6.84s` with zero errors.
- **Artifacts**: `dist/index.html` (0.86 kB), `dist/assets/index-DmaaDGJo.css` (261.32 kB), `dist/assets/index-DG0jYT6l.js` (491.94 kB).

---

## 18. Milestone M15 Step 5: Admin Customer Order Oversight & Saga Tracking UI Verification

### 18.1 Discovered Backend API Contracts
- `GET /api/orders/admin`:
  - **Authorization**: Required `ROLE_ADMIN` (forwarded via Gateway `X-User-Roles`).
  - **Response**: `ApiResponse<List<OrderResponse>>` containing all customer orders across the platform.
  - **Capability**: Returns flat list without backend pagination or query filtering. Handled with client-side search, filtering, and summary metrics.
- `GET /api/orders/{id}`:
  - **Authorization**: Order owner (`X-User-Id`) or platform administrator (`ROLE_ADMIN` in `X-User-Roles`).
  - **Response**: `ApiResponse<OrderResponse>` with customer email, shipping address, status, cancellation reason, total amount, timestamp, and array of authoritative line items (`OrderItemResponse`).
- `GET /api/payments/order/{orderId}`:
  - **Authorization**: Forwarded via Gateway.
  - **Response**: `ApiResponse<PaymentTransaction>` (`paymentId`, `orderId`, `transactionId`, `amount`, `paymentMethod`, `status`, `failureReason`, `createdAt`).
  - **Edge Case**: Returns HTTP 404 if order failed prior to payment stage (e.g., inventory rejected). Handled cleanly by Redux slice.
- `GET /api/notifications/order/{orderId}`:
  - **Authorization**: Required `ROLE_ADMIN` or order owner.
  - **Response**: `ApiResponse<List<NotificationResponse>>` containing chronological Kafka event logs (`ORDER_CREATED`, `INVENTORY_RESERVED`, `INVENTORY_REJECTED`, `PAYMENT_SUCCEEDED`, `PAYMENT_FAILED`).
- **Architectural Gaps Documented**:
  - `inventory-service` has no per-order reservation endpoint (`GET /api/inventory/order/{orderId}`). Inventory status is evidenced through the order status, cancellation reasons, and notification audit events.
  - No refund endpoint exists. No refund action is provided in the UI.

### 18.2 Frontend Automated Unit Tests (`npm test --prefix frontend`)
- **Execution Command**: `npm test --prefix frontend`
- **Total Tests Run**: **40 tests, 0 failures, 0 errors** (BUILD SUCCESS).
  - Cart Redux Logic (`test-cart-logic.js`): 10 tests passed.
  - Admin Auth & Route Protection (`test-admin-auth.js`): 10 tests passed.
  - Admin Product Management (`test-admin-products.js`): 10 tests passed.
  - Admin Customer Order Oversight (`test-admin-orders.js`): 10 tests passed:
    1. Order Search & Filter: Filtering by ID, email, and SKU.
    2. Order Search & Filter: Filtering by order status (`ALL`, `CONFIRMED`, `CANCELLED`, `PENDING`).
    3. Order Search & Filter: Filtering by simulated outcome mode (`SIMULATED_SUCCESS`, `SIMULATED_FAILURE`).
    4. Saga Visualization: Confirmed order produces 4 completed stages.
    5. Saga Visualization: Payment failure correctly reflects initial inventory reservation and compensation.
    6. Saga Visualization: Out-of-stock cancels Saga before payment (Stage 2 failed, Stage 3 skipped).
    7. Payment Simulator Auditing: Mandatory demo disclaimer notice present (`DEMO / SIMULATED PAYMENT ONLY`).
    8. Authoritative Line Items: Computed line totals match order total amount.
    9. Redux Order Reducer: Handles admin actions and state transitions (`fetchAllOrdersAdmin`, `fetchPaymentByOrderId`, clearing reducers).
    10. Event Trail Fallback: System gracefully handles empty notification logs without fictitious timestamps.

### 18.3 Frontend Production Build
- **Execution Command**: `npm run build --prefix frontend`
- **Results**: `✓ 2137 modules transformed. built in 6.62s` with zero errors.
- **Artifacts**: `dist/index.html` (0.86 kB), `dist/assets/index-OgKjLUf8.css` (262.36 kB), `dist/assets/index-BwaS5UB7.js` (522.74 kB).

---

## 19. Milestone M15 Step 6: Admin Inventory & Stock Management Verification

### 19.1 Inventory Service Replenishment Endpoint Contract
- **Endpoint**: `POST /api/inventory/{productId}/replenish`
- **Security & Authorization**:
  - Independent zero-trust cryptographic verification using `JwtUtil` (`app.jwt.secret`).
  - Required role: strictly `ROLE_ADMIN`.
  - Rejects missing, invalid, or expired tokens with HTTP `401 Unauthorized`.
  - Rejects non-admin (customer) tokens with HTTP `403 Forbidden`.
  - Ignores caller-supplied spoofed `X-User-Roles` headers unless accompanied by a cryptographically signed administrator JWT.
- **Request Validation (`ReplenishmentRequest`)**:
  - `quantity`: Required, integer, $\ge 1$, $\le 10,000$. Returns HTTP `400 Bad Request` with field error map.
  - `productId`: Path variable. Returns HTTP `404 Not Found` (`ResourceNotFoundException`) if product does not exist in inventory.
- **Concurrency & Stock Integrity**:
  - Uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`) on the target `inventory_items` row.
  - Increases `availableQuantity` atomically by the requested amount.
  - `reservedQuantity` is strictly unmodified (active Saga reservations preserved).
  - Version column `@Version private Long version;` incremented on commit.

### 19.2 Backend Automated Test Results
- **Inventory Service Suite (`mvn test -pl inventory-service`)**:
  - Total Tests: **40 tests, 0 failures, 0 errors, 0 skipped** (BUILD SUCCESS).
  - `InventoryControllerTest`: 12 tests passed (valid admin replenishment, missing auth header, invalid/expired JWT, customer JWT 403, spoofed header rejection, zero quantity 400, negative quantity 400, >10k quantity 400, non-existent product 404, get stock 200, get stock 404, get all stock 200).
  - `InventoryServiceTest`: 23 tests passed (replenish increases available, reserved unchanged, multiple replenishments accumulate, unknown product throws not found, non-positive quantity throws illegal argument, excessive quantity throws illegal argument, reservation tests, compensation tests, poison pill tests).
  - `InventoryEventListenerTest`: 5 tests passed.
- **Complete Monorepo Test Suite (`mvn test`)**:
  - Total Tests: **187 tests, 0 failures, 0 errors, 0 skipped** across all 8 microservices (BUILD SUCCESS):
    - `ecommerce-parent`: 1 validation passed
    - `api-gateway`: 24 tests passed
    - `auth-service`: 15 tests passed
    - `product-service`: 19 tests passed
    - `order-service`: 29 tests passed
    - `inventory-service`: 40 tests passed
    - `payment-service`: 26 tests passed
    - `notification-service`: 25 tests passed

### 19.3 Frontend Automated Unit Tests (`npm test --prefix frontend`)
- **Execution Command**: `npm test --prefix frontend`
- **Total Tests Run**: **50 tests, 0 failures, 0 errors** across 5 test suites (BUILD SUCCESS).
  - Cart Redux Logic (`test-cart-logic.js`): 10 tests passed.
  - Admin Auth & Route Protection (`test-admin-auth.js`): 10 tests passed.
  - Admin Product Management (`test-admin-products.js`): 10 tests passed.
  - Admin Customer Order Oversight (`test-admin-orders.js`): 10 tests passed.
  - Admin Inventory Management (`test-admin-inventory.js`): 10 tests passed:
    1. KPI Calculations: Aggregates total SKUs, available units, reserved units, low stock count, out of stock count.
    2. Stock Status Classification: Correctly buckets available quantities (`IN_STOCK` >10, `LOW_STOCK` 1..10, `OUT_OF_STOCK` 0).
    3. Search & Filter: Filtering by SKU, product name, and category.
    4. Reserved Stock Filter: Filters SKUs with active Saga reservations.
    5. Replenishment Validation: Enforces positive integer in range 1 – 10,000.
    6. Projected Quantity: Adds requested amount to available only (excluding reserved units).
    7. Redux Reducer: `replenishStock.fulfilled` mutates item in place with server response.
    8. Redux Reducer: `replenishStock.rejected` sets `replenishError` and resets `replenishing`.
    9. Security Errors: Reducer handles 401 Unauthorized and 403 Forbidden rejections.
    10. Feedback Clearing: `clearInventoryFeedback` resets alert banners cleanly.

### 19.4 Frontend Production Build
- **Execution Command**: `npm run build --prefix frontend`
- **Results**: `✓ 2140 modules transformed. built in 10.52s` with zero errors.
- **Artifacts**: `dist/index.html` (0.86 kB), `dist/assets/index-C-sn3ZwX.css` (265.36 kB), `dist/assets/index-D7GQMlqR.js` (542.39 kB).
