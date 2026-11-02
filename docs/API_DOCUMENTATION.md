# API Documentation — Event-Driven E-Commerce

All REST endpoints return standard `ApiResponse<T>` objects:
```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": { ... },
  "timestamp": "2026-10-29T10:15:30Z"
}
```

---

## 1. Product Service (`http://localhost:8081` or via Gateway `http://localhost:8080/api/products`)

### `GET /api/products`
Retrieves paginated active products with optional search query and category filtering.
- **Query Parameters**:
  - `page` (optional, default `0`)
  - `size` (optional, default `10`)
  - `category` (optional, string)
  - `search` (optional, string)
- **Response `200 OK`**:
  ```json
  {
    "success": true,
    "message": "Products retrieved successfully",
    "data": {
      "content": [
        {
          "id": 1,
          "name": "Mechanical Keyboard RGB",
          "description": "Ergonomic tactile switches with per-key RGB backlight",
          "price": 89.99,
          "sku": "TECH-KB-01",
          "imageUrl": "https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=600&q=80",
          "category": "Electronics",
          "stockQuantity": 50,
          "active": true
        }
      ],
      "totalPages": 1,
      "totalElements": 8,
      "currentPage": 0
    },
    "timestamp": "2026-10-29T10:15:30Z"
  }
  ```

### `GET /api/products/{id}`
Retrieves single product details by ID. Public endpoint (no authentication required). Cached in Redis under key `products::detail::{id}`.

### `POST /api/products`
Creates a new product. Evicts Redis cache `products`.
- **Authorization**: Requires Bearer JWT through API Gateway (`401 Unauthorized` if missing) and `ROLE_ADMIN` role (`403 Forbidden` if customer).
- **Request Body**:
  ```json
  {
    "name": "Wireless Noise Cancelling Headphones",
    "description": "Premium over-ear headphones with 40-hour battery life",
    "price": 199.99,
    "sku": "TECH-HP-02",
    "imageUrl": "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=600&q=80",
    "category": "Electronics",
    "stockQuantity": 30
  }
  ```

### `PUT /api/products/{id}`
Updates an existing product by ID. Evicts Redis cache `products`.
- **Authorization**: Requires Bearer JWT through API Gateway (`401 Unauthorized` if missing) and `ROLE_ADMIN` role (`403 Forbidden` if customer).

### `DELETE /api/products/{id}`
Soft deletes a product by ID (sets active = false). Evicts Redis cache `products`.
- **Authorization**: Requires Bearer JWT through API Gateway (`401 Unauthorized` if missing) and `ROLE_ADMIN` role (`403 Forbidden` if customer).

### `GET /api/categories`
Returns distinct list of product categories with active product counts. Public endpoint (no authentication required). Cached in Redis.

---

## 2. Order Service (`http://localhost:8082` or via Gateway `http://localhost:8080/api/orders`)

All order endpoints require authentication via Bearer JWT through the API Gateway, which forwards identity headers `X-User-Id`, `X-User-Email`, and `X-User-Roles`.

> [!NOTE]
> **Fail-Closed Authoritative Catalog Price Validation (M14)**: To eliminate client-side price tampering and ensure zero trust, `OrderService` validates every requested item against `product-service:8081` via `ProductCatalogClient`. If Product Service is unavailable, the product does not exist, the product is inactive, or the catalog price is invalid (<= 0), the order is rejected immediately with an API error. No order is persisted to MySQL and no `OrderCreatedEvent` is published. Client-submitted prices and names are never used as a fallback.

### `POST /api/orders`
Creates a new order in `PENDING` state and emits `OrderCreatedEvent` to Kafka topic `order-created-events`.
- **Headers**:
  - `Authorization: Bearer <JWT>`
- **Authoritative Validation & Client Price Independence**:
  - Client-supplied `unitPrice`, `productName`, and `productSku` are optional and never determine saved order values.
  - The authoritative `product-service` supplies the official price, title, and SKU.
  - Returns `503 Service Unavailable` (`ProductServiceUnavailableException`) if `product-service` is unreachable or times out.
  - Returns `400 Bad Request` if a product does not exist, has `active: false` (or null/unknown status), or has an invalid catalog price.
- **Request Body**:
  ```json
  {
    "customerName": "Demo Customer",
    "shippingAddress": "42 Market Street, San Francisco, CA 94105",
    "paymentSimulateMode": "SUCCESS",
    "items": [
      {
        "productId": 1,
        "quantity": 2
      },
      {
        "productId": 2,
        "quantity": 1
      }
    ]
  }
  ```
- **Response `201 Created`**:
  ```json
  {
    "success": true,
    "message": "Order placed successfully. Saga initiated.",
    "data": {
      "id": 1,
      "orderNumber": "ORD-1",
      "customerId": 1,
      "customerEmail": "customer@test.com",
      "customerName": "Demo Customer",
      "shippingAddress": "42 Market Street, San Francisco, CA 94105",
      "paymentSimulateMode": "SUCCESS",
      "totalAmount": 309.97,
      "status": "PENDING",
      "cancellationReason": null,
      "items": [
        {
          "id": 1,
          "productId": 1,
          "productName": "Mechanical Keyboard RGB",
          "productSku": "TECH-KB-01",
          "unitPrice": 89.99,
          "quantity": 2,
          "subtotal": 179.98
        },
        {
          "id": 2,
          "productId": 2,
          "productName": "Wireless Noise Cancelling Headphones",
          "productSku": "AUD-HP-02",
          "unitPrice": 129.99,
          "quantity": 1,
          "subtotal": 129.99
        }
      ],
      "createdAt": "2026-10-30T09:32:19.467812Z",
      "updatedAt": "2026-10-30T09:32:19.467812Z"
    },
    "timestamp": "2026-10-30T09:32:19.520121Z"
  }
  ```

### `GET /api/orders/{id}`
Returns order details and current status (`PENDING`, `CONFIRMED`, `CANCELLED`).
- **Authorization**: Customers can only view their own orders. Attempting to view another customer's order returns `403 Forbidden`. Administrators (`ROLE_ADMIN`) can view any order.

### `GET /api/orders/my-orders`
Returns the complete chronological order history for the currently authenticated customer based on their verified JWT claims.

### `GET /api/orders/admin`
Returns all orders across all customers in the system. Requires `ROLE_ADMIN` in the JWT token (returns `403 Forbidden` for standard customers).

### Order State Transitions & Saga Lifecycle
Orders strictly follow a finite state machine enforced by database row locks and database-level event deduplication:
- `PENDING` -> Initial state upon order submission via `POST /api/orders`. Emits `OrderCreatedEvent`.
- `PENDING` &rarr; `CONFIRMED`: Applied when `payment-succeeded-events` arrives. Fulfills order.
- `PENDING` &rarr; `CANCELLED`: Applied when `payment-failed-events` or `inventory-rejected-events` arrives. Sets `cancellation_reason`.
- `CONFIRMED` / `CANCELLED`: **Terminal States**. Immutable. Late or out-of-order Kafka events (such as delayed `PaymentSucceededEvent` arriving after order was already cancelled) are safely discarded to prevent state corruption.
- **Idempotency**: All processed events are tracked in `order_processed_events` (`order_id`, `event_type`) with a unique database constraint. Concurrent duplicate Kafka deliveries are detected and discarded without side-effects.


---

## 4. Inventory Service (`http://localhost:8083` or `/api/inventory` via Gateway)

### `GET /api/inventory`
Returns real-time stock levels for all products in the catalog.
- **Access**: Public / Authenticated
- **Response `200 OK`**:
  ```json
  {
    "success": true,
    "message": "Inventory list retrieved successfully",
    "data": [
      {
        "id": 1,
        "productId": 1,
        "sku": "TECH-KB-01",
        "availableQuantity": 37,
        "reservedQuantity": 8,
        "version": 5,
        "updatedAt": "2026-10-30T12:15:56Z"
      }
    ],
    "timestamp": "2026-10-30T12:16:00Z"
  }
  ```

### `GET /api/inventory/{productId}`
Returns available and reserved stock quantities for a specific product.
- **Access**: Public / Authenticated
- **Response `200 OK`**:
  ```json
  {
    "success": true,
    "message": "Stock retrieved successfully",
    "data": {
      "id": 1,
      "productId": 1,
      "sku": "TECH-KB-01",
      "availableQuantity": 37,
      "reservedQuantity": 8,
      "version": 5,
      "updatedAt": "2026-10-30T12:15:56Z"
    },
    "timestamp": "2026-10-30T12:16:00Z"
  }
  ```
- **Response `404 Not Found`**: Returned when the requested `productId` does not exist in the inventory catalog.

---

## 5. Payment Service (`http://localhost:8084` or `/api/payments` via Gateway)

> [!NOTE]
> **DEMO / SIMULATION ENVIRONMENT**: The Payment Service uses a deterministic `MockPaymentGateway` to safely simulate credit card and UPI transactions without connecting to actual financial networks or processing real cardholder data.

### `GET /api/payments/order/{orderId}`
Returns the simulated payment transaction record for the specified order.
- **Access**: Public / Authenticated
- **Response `200 OK` (Authorized Transaction)**:
  ```json
  {
    "success": true,
    "message": "Payment transaction retrieved",
    "data": {
      "id": 5,
      "orderId": 9101,
      "customerId": 1,
      "amount": 149.99,
      "paymentMethod": "CARD_DEMO",
      "paymentSimulateMode": "SUCCESS",
      "status": "SUCCESS",
      "transactionReference": "TXN-AUTH-630556c7-ba25-45a9-ad7a-98936a7faf1f",
      "failureReason": null,
      "demoNotice": "DEMO/SIMULATED PAYMENT ONLY - No actual banking funds transferred",
      "createdAt": "2026-10-30T14:24:20.770182Z",
      "updatedAt": "2026-10-30T14:24:20.770182Z"
    },
    "timestamp": "2026-10-30T14:24:25.098641600Z"
  }
  ```
- **Response `200 OK` (Declined Transaction)**:
  ```json
  {
    "success": true,
    "message": "Payment transaction retrieved",
    "data": {
      "id": 6,
      "orderId": 9202,
      "customerId": 1,
      "amount": 199.98,
      "paymentMethod": "CARD_DEMO",
      "paymentSimulateMode": "FAILURE",
      "status": "FAILED",
      "transactionReference": "TXN-DECLINED-a2be90f2-4281-47ea-b72f-ea7e0db9ecaa",
      "failureReason": "Simulated card decline: insufficient test funds or card issuer decline",
      "demoNotice": "DEMO/SIMULATED PAYMENT ONLY - No actual banking funds transferred",
      "createdAt": "2026-10-30T14:24:45.123456Z",
      "updatedAt": "2026-10-30T14:24:45.123456Z"
    },
    "timestamp": "2026-10-30T14:24:48.098641600Z"
  }
  ```

---

## 6. Notification Service (`http://localhost:8085` or `/api/notifications` via Gateway)

> [!NOTE]
> **AUDIT & TIMELINE RECORDING**: The Notification Service acts as an immutable audit logger and customer event timeline engine. It records customer-facing notification audit events generated during the distributed Saga and exposes authenticated queries for storefront order progress tracking. (Real third-party SMS/Email dispatch gateways are simulated as audit records).

### `GET /api/notifications/order/{orderId}`
Retrieves the chronological notification audit trail for a specific order (newest first).

- **Headers**:
  - `X-User-Id`: Authenticated user ID (injected by API Gateway from verified JWT)
  - `X-User-Roles`: Roles associated with user (e.g. `ROLE_CUSTOMER`, `ROLE_ADMIN`)
  - `X-User-Email`: Customer email address
- **Authorization**:
  - Customers may only view notifications for orders they placed. Requesting notifications for an order owned by another customer returns `403 Forbidden`.
  - Administrators (`ROLE_ADMIN`) may view notification history for any order.
  - Unauthenticated requests return `401 Unauthorized`.
  - Non-existent orders return `404 Not Found`.
- **Response `200 OK`**:
  ```json
  {
    "success": true,
    "message": "Notification logs retrieved successfully",
    "data": [
      {
        "id": 45,
        "eventId": "9ac9d9a4-a3f0-49fa-b77d-aea37c970a30",
        "orderId": 8,
        "customerId": 1,
        "recipientEmail": "customer@test.com",
        "eventType": "PAYMENT_SUCCEEDED",
        "subject": "Order #8 Confirmed!",
        "message": "Payment authorized (Ref: TXN-AUTH-32345). Your order is now confirmed and will be shipped soon!",
        "correlationId": "CORR-8-27067f94",
        "status": "RECORDED",
        "createdAt": "2026-10-30T17:16:42.430732Z"
      },
      {
        "id": 36,
        "eventId": "f5d2b298-7b9c-42c0-af85-0ae2fb397076",
        "orderId": 8,
        "customerId": 1,
        "recipientEmail": "customer@test.com",
        "eventType": "INVENTORY_RESERVED",
        "subject": "Warehouse Stock Reserved for Order #8",
        "message": "All items have been verified and reserved. Awaiting payment processing.",
        "correlationId": "CORR-8-27067f94",
        "status": "RECORDED",
        "createdAt": "2026-10-30T17:16:41.711313Z"
      },
      {
        "id": 19,
        "eventId": "047d1f44-623d-4b2a-9d78-8f25ba0511b4",
        "orderId": 8,
        "customerId": 1,
        "recipientEmail": "customer@test.com",
        "eventType": "ORDER_CREATED",
        "subject": "Order #8 Placed",
        "message": "Your order totaling $89.99 was placed successfully. Securing warehouse inventory.",
        "correlationId": "CORR-8-27067f94",
        "status": "RECORDED",
        "createdAt": "2026-10-30T17:16:36.891475Z"
      }
    ],
    "timestamp": "2026-10-30T17:17:03.625Z"
  }
  ```

      "demoNotice": "DEMO/SIMULATED PAYMENT ONLY - No actual banking funds transferred",
      "createdAt": "2026-10-30T14:26:21.801243Z",
      "updatedAt": "2026-10-30T14:26:21.801243Z"
    },
    "timestamp": "2026-10-30T14:26:22.098641600Z"
  }
  ```
- **Response `404 Not Found`**: Returned when no payment transaction exists for the specified `orderId`.


---

## 7. Frontend Checkout & Live Saga Tracking Flow (Milestone M13)

The React client interacts exclusively with the Spring Cloud API Gateway on `http://localhost:8080/api`:

```
[Browser / React Storefront]
       │
       ▼ (Axios with Bearer Token from localStorage)
[API Gateway :8080]
       │
       ├── /api/auth/login ──────────> [auth-service :8086]
       ├── /api/products ────────────> [product-service :8081]
       ├── /api/orders ──────────────> [order-service :8082]
       │       └── POST /api/orders (Creates order PENDING -> publishes OrderCreatedEvent)
       │       └── GET /api/orders/my-orders (Customer order history)
       │       └── GET /api/orders/{id} (Order status polling)
       └── /api/notifications/order/{id} ──> [notification-service :8085]
               └── Event audit log polling for real-time stepper
```

### Polling & Stepper Lifecycle
1. When navigating to `/orders/{id}`, the React client initiates a 2-second polling interval against `GET /api/orders/{id}` and `GET /api/notifications/order/{id}`.
2. While `order.status === 'PENDING'`, the stepper visualizes active choreography.
3. Upon reaching a terminal state (`CONFIRMED` or `CANCELLED`), polling is immediately terminated (`clearInterval`), and a non-intrusive toast alert notifies the user.
4. When unmounting the component, any active polling interval is cleanly torn down to prevent memory leaks.
