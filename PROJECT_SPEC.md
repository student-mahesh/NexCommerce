# PROJECT_SPEC.md — Functional & Non-Functional Specifications

## 1. Executive Summary & Business Problem

In high-volume e-commerce architectures, traditional monolithic applications and synchronous REST chains suffer from critical weaknesses:
- **Tight Coupling & Cascading Failures**: If the payment gateway or inventory service is slow or down, the entire checkout process freezes, blocking orders and degrading user experience.
- **Distributed Inconsistency**: Updating inventory, debiting payments, and creating orders across multiple databases without a distributed transaction protocol leads to ghost stock, phantom orders, and double billing.
- **Scalability Bottlenecks**: High-read traffic on the product catalog overloads databases unless intelligent caching (Redis) is applied.

**Solution**: This project implements an **Event-Driven E-Commerce Microservices Architecture** using **Java 21, Spring Boot 3, Apache Kafka, Redis, MySQL, Spring Cloud Gateway, and React with Vite**. It implements the **Choreography-based Saga Pattern** to ensure eventual consistency, reliable compensation on payment failures, sub-millisecond catalog reads via Redis caching, and robust security through Google OAuth2 and JWT authentication.

---

## 2. User Roles & Persona Matrix

| Role | Description | Key Capabilities |
| :--- | :--- | :--- |
| **Guest / Anonymous User** | Unauthenticated prospective customer browsing the storefront. | - Browse product catalog with category filtering and search.<br>- View detailed product information and stock availability.<br>- Add items to local shopping cart.<br>- Initiate sign-in or registration upon checkout. |
| **Customer (Authenticated)** | Verified shopper with a persistent account. | - Register/Login via Email & Password or Google OAuth2.<br>- Manage cart and adjust item quantities.<br>- Proceed through checkout with shipping address details.<br>- Simulate payment with deterministic test modes (Success or Failure).<br>- View live order status updates (`PENDING` -> `CONFIRMED` / `CANCELLED`).<br>- Browse past order history and invoice details. |
| **Admin** | Store operations manager with administrative privileges. | - Access protected Admin Dashboard.<br>- Create, update, and soft-delete products.<br>- Manage categories and real-time inventory stock levels.<br>- View all customer orders across the platform and track event histories. |

---

## 3. End-to-End Business Flow & Saga Choreography

The application implements an automated, event-driven purchase flow using Apache Kafka:

```
[Customer Browser] 
       | (1) POST /api/orders (REST)
       v
[api-gateway (8080)]
       | Routing & JWT Validation
       v
[order-service (8082)] ---> Saves Order as PENDING in order_db
       |
       | (2) Publishes OrderCreatedEvent
       v
    [Kafka Topic: order-created-events]
       |
       v (Consumed by inventory-service)
[inventory-service (8083)]
       | Checks stock in inventory_db
       |-- Stock Available?
             |--> YES: Reserves stock & Publishes InventoryReservedEvent
             |--> NO:  Publishes InventoryRejectedEvent
       v
    [Kafka Topic: inventory-reserved-events]
       |
       v (Consumed by payment-service)
[payment-service (8084)]
       | Simulates payment transaction
       |-- Payment Approved?
             |--> YES: Publishes PaymentSucceededEvent
             |--> NO:  Publishes PaymentFailedEvent
       v
    [Kafka Topic: payment-succeeded-events / payment-failed-events]
       |
       +---> [order-service] Updates Order status to CONFIRMED or CANCELLED
       |
       +---> [inventory-service] (On PaymentFailed) Executes Compensation:
       |        Releases reserved stock & Publishes InventoryReleasedEvent
       |
       +---> [notification-service (8085)] Logs order confirmation or failure audit trail
```

---

## 4. Microservice Functional Requirements

### 4.1 `api-gateway` (Port 8080)
- Single reverse proxy for all frontend client traffic.
- Route mapping to downstream microservices:
  - `/api/auth/**` -> `auth-service:8086`
  - `/api/products/**` -> `product-service:8081`
  - `/api/categories/**` -> `product-service:8081`
  - `/api/orders/**` -> `order-service:8082`
  - `/api/inventory/**` -> `inventory-service:8083`
  - `/api/payments/**` -> `payment-service:8084`
  - `/api/notifications/**` -> `notification-service:8085`
- Global CORS configuration allowing requests from the React frontend (`http://localhost:5173` and `http://localhost:3000`).
- Reactive JWT Authentication Filter:
  - Intercepts protected requests.
  - Validates JWT signature and expiration.
  - Extracts `userId`, `email`, and `roles`.
  - Injects headers: `X-User-Id`, `X-User-Email`, `X-User-Roles` into downstream requests.

### 4.2 `auth-service` (Port 8086)
- User entity management with roles (`ROLE_CUSTOMER`, `ROLE_ADMIN`).
- Endpoints:
  - `POST /api/auth/register`: Create account with BCrypt password hashing.
  - `POST /api/auth/login`: Authenticate and issue signed JWT.
  - `POST /api/auth/google`: Verify Google OAuth2 token and issue platform JWT.
  - `GET /api/auth/me`: Return authenticated profile.
- Secure token claims: `sub` (userId), `email`, `roles`, `iat`, `exp` (24-hour expiration).

### 4.3 `product-service` (Port 8081)
- Product entity: `id`, `name`, `description`, `price`, `sku`, `imageUrl`, `category`, `active`, `createdAt`.
- Endpoints:
  - `GET /api/products`: Paginated list of active products with category and search filter.
  - `GET /api/products/{id}`: Fetch product details.
  - `POST /api/products`: Admin creation (evicts cache).
  - `PUT /api/products/{id}`: Admin update (evicts cache).
  - `DELETE /api/products/{id}`: Admin soft delete.
  - `GET /api/categories`: Unique list of product categories.
- **Redis Cache-Aside Pattern**:
  - `products::list` cached with a 15-minute Time-To-Live (TTL).
  - `products::detail::{id}` cached on demand.
  - Mutation operations (`POST`, `PUT`, `DELETE`) trigger `@CacheEvict` to maintain cache freshness.

### 4.4 `order-service` (Port 8082)
- Order entity: `id`, `orderNumber`, `customerId`, `customerEmail`, `totalAmount`, `status` (`PENDING`, `CONFIRMED`, `CANCELLED`), `shippingAddress`, `createdAt`.
- OrderItem entity: `id`, `orderId`, `productId`, `productName`, `quantity`, `unitPrice`, `subtotal`.
- Endpoints:
  - `POST /api/orders`: Create new order in `PENDING` state and publish `OrderCreatedEvent`.
  - `GET /api/orders/{id}`: Retrieve order details and current status.
  - `GET /api/orders/customer/{customerId}`: Retrieve customer order history.
  - `GET /api/orders/admin`: Retrieve all orders (Admin role).
- Kafka Listeners:
  - Listens to `PaymentSucceededEvent` -> Updates order status to `CONFIRMED`.
  - Listens to `PaymentFailedEvent` or `InventoryRejectedEvent` -> Updates order status to `CANCELLED`.

### 4.5 `inventory-service` (Port 8083)
- Inventory entity: `id`, `productId`, `sku`, `availableQuantity`, `reservedQuantity`, `updatedAt`.
- Endpoints:
  - `GET /api/inventory/{productId}`: Check stock availability.
  - `POST /api/inventory/adjust`: Admin manual stock level adjustment.
- Kafka Listeners:
  - Listens to `OrderCreatedEvent`: Validates stock. If sufficient, increments `reservedQuantity`, decrements `availableQuantity`, and emits `InventoryReservedEvent`. If insufficient, emits `InventoryRejectedEvent`.
  - Listens to `PaymentFailedEvent` (Compensation Saga): Decrements `reservedQuantity`, restores `availableQuantity`, and emits `InventoryReleasedEvent`.

### 4.6 `payment-service` (Port 8084)
- Payment entity: `id`, `orderId`, `customerId`, `amount`, `paymentMethod`, `status` (`SUCCESS`, `FAILED`), `transactionReference`, `createdAt`.
- Deterministic Simulation Engine:
  - Allows UI checkout to trigger either a `SUCCESS` flow (normal card) or a `FAILURE` flow (e.g. invalid test card `4000 0000 0000 0002` or simulation flag) to demonstrate saga compensation.
- Kafka Listeners:
  - Listens to `InventoryReservedEvent` -> Processes payment -> Publishes `PaymentSucceededEvent` or `PaymentFailedEvent`.

### 4.7 `notification-service` (Port 8085)
- Notification entity: `id`, `orderId`, `recipientEmail`, `type` (`ORDER_CONFIRMED`, `ORDER_CANCELLED`, `STOCK_ALERT`), `content`, `sentAt`.
- Kafka Listeners:
  - Listens to `PaymentSucceededEvent` -> Records confirmation notification.
  - Listens to `PaymentFailedEvent` / `InventoryRejectedEvent` -> Records cancellation notice.
- Endpoints:
  - `GET /api/notifications/order/{orderId}`: Audit log of notifications dispatched for an order.

---

## 5. Frontend UI/UX Specifications

- **Framework**: React with Vite, JavaScript (ES6+), React Router v6, Redux Toolkit, Axios.
- **Styling Architecture**: Harmonious Bootstrap 5.3 (grid layout, standard forms, modal structures) + Tailwind CSS 3.4 (modern typography, slate color palette, subtle hover transitions, badges, and status cards).
- **Required Pages**:
  1. **Home / Storefront**: Hero banner, featured categories, top deals, trust badges.
  2. **Product Catalog & Filters**: Grid display, category sidebar, price filter, search input, pagination/sorting.
  3. **Product Details**: Product image, price, description, SKU, dynamic stock availability indicator ("In Stock", "Only X left", "Out of Stock"), quantity selector, "Add to Cart" button.
  4. **Shopping Cart**: Item list, thumbnail, quantity increment/decrement, price calculation, item removal, cart summary, proceed to checkout button.
  5. **Checkout**: Customer contact info, shipping address form, order summary, simulated payment option selector (Demo Mode: "Simulate Success" vs "Simulate Failure" to showcase Saga compensation).
  6. **Order Confirmation & Tracking**: Real-time visual progress stepper:
     `Order Created` -> `Inventory Reserved` -> `Payment Processed` -> `Order Confirmed` (or `Compensated / Cancelled`).
  7. **Customer Order History**: Historical orders with status badges (`PENDING`, `CONFIRMED`, `CANCELLED`), date, totals, and clickable details.
  8. **Admin Product & Inventory Management**: Table of products with search, "Add Product" modal, "Edit Product" modal, real-time stock adjustment inline.
  9. **Admin Order Management**: Overview of all customer orders, filter by status, view event timeline.
  10. **Authentication Pages**: Login and Registration forms with validation, Google OAuth2 sign-in button, and demo one-click credentials ("Demo Customer", "Demo Admin").

---

## 6. Non-Functional Requirements & Acceptance Criteria

| Criteria | Standard | Verification Method |
| :--- | :--- | :--- |
| **Response Time (Catalog)** | < 50ms for cached product queries | Redis CLI monitor & browser network timings. |
| **Data Consistency** | Eventual consistency via Saga; zero orphaned reserved inventory on failed payment. | Trigger payment failure test; verify inventory table stock level restores to original value. |
| **Security** | Zero hard-coded credentials; BCrypt hashed passwords; signed JWT verification. | Code review of repository, `.env.example` inspection, unauthorized HTTP 401 tests. |
| **Code Quality** | Simple, readable Java 21; standard Spring annotations; descriptive error messages. | Automated test suite execution (JUnit 5 + Mockito). |
| **Deployability** | One-command startup via Docker Compose without port collisions on host. | `docker compose up -d` passes health checks cleanly. |
