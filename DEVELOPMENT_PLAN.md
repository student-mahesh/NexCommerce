# DEVELOPMENT_PLAN.md — 3-Day Milestone Execution Plan & Verification Tracker

## 1. Project Timeline Overview

This development plan establishes a structured 3-day roadmap for developing, testing, verifying, and defending the **Event-Driven E-Commerce Microservices** project in a technical interview.

```
+-----------------------------------------------------------------------------------------+
| DAY 1: Foundation & Product Catalogue                                                  |
| - Prerequisites & System Inspection                                                     |
| - Documentation Baseline (README, ARCHITECTURE, PROJECT_SPEC, AGENTS, DEV_PLAN)         |
| - Docker Compose Infrastructure (MySQL, Redis, Kafka on isolated ports)                 |
| - Service Scaffolding & Root Maven Configuration                                        |
| - Product Service (REST CRUD + Redis Cache-Aside + Seed Data)                          |
| - React Storefront (Vite + Bootstrap + Tailwind + Catalog + Details)                   |
| - Day 1 Verification Checkpoint                                                         |
+-----------------------------------------------------------------------------------------+
| DAY 2: Orders, Kafka Choreography & Authentication                                      |
| - Auth Service & API Gateway Security (Google OAuth2 + JWT + Header Forwarding)         |
| - Order Service (Checkout + State Machine + OrderCreated Kafka Producer)                |
| - Inventory Service (Stock Reservation + Compensation Release Logic)                   |
| - Payment Service (Deterministic Simulation Engine + Succeeded/Failed Events)           |
| - Full Choreography Saga Integration & Testing                                          |
| - Notification Service (Audit Log + Event Listeners)                                   |
| - Frontend Checkout, Payment Simulation & Live Order Stepper Tracking                   |
| - Day 2 Verification Checkpoint                                                         |
+-----------------------------------------------------------------------------------------+
| DAY 3: Quality, UI Polish, Automated Testing & GitHub                                   |
| - Admin Dashboard (Product/Stock Management & Order Oversight)                         |
| - Customer Order History Page & Order Invoices                                          |
| - Unit & Integration Test Suites (JUnit 5 + Mockito)                                    |
| - Error Resilience & Compensation Verification Tests                                    |
| - Swagger / OpenAPI Documentation Verification                                          |
| - Git Initialization & GitHub Push Verification                                         |
| - Technical Interview Preparation Guide (Architecture, Kafka, Redis, OAuth2/JWT)       |
+-----------------------------------------------------------------------------------------+
```

---

## 2. Milestone Checklist & Live Status Tracker

| Milestone | Scope / Feature | Day | Status | Verification Checkpoint / Test Command |
| :---: | :--- | :---: | :---: | :--- |
| **M1** | Environment inspection, tool audit, port scan, and 5 documentation files. | Day 1 | **VERIFIED** | Java 21, Maven 3.10, Docker, Node 26, Git verified; 5 core docs created. |
| **M2** | Docker Compose infrastructure & `.env.example` setup with conflict-free host ports (MySQL 3309, Redis 6380, Kafka 9094). | Day 1 | **VERIFIED** | All 3 containers running `Up (healthy)`. Schemas and topics verified. |
| **M3** | Root Maven multi-module structure and service parent configuration. | Day 1 | **VERIFIED** | `mvn clean compile` passes cleanly across parent and all 7 services. |
| **M4** | `product-service` implementation: JPA entities, repository, Redis cache-aside, REST CRUD, and seed catalog. | Day 1 | **VERIFIED** | 8 seed products in MySQL; Redis keys `categories::all` and `products::*` confirmed. |
| **M5** | React frontend initialization with Vite, Bootstrap + Tailwind, Redux Toolkit, and Storefront pages. | Day 1 | **VERIFIED** | `npm run build` passes; dev server live on `http://localhost:5173`. |
| **M6** | **Day 1 Milestone Checkpoint**: End-to-end product browsing with Redis caching and UI verified. | Day 1 | **VERIFIED** | Storefront queries `/api/products` via Vite proxy; all 8 products returned. |
| **M7** | `auth-service` and `api-gateway` reactive JWT authentication filter, Google OAuth2 integration, and header forwarding. | Day 2 | **VERIFIED** | Customer/Admin authentication, BCrypt check, JWT validation, 401 on expired/tampered/missing tokens, and identity header injection verified live. |
| **M8** | `order-service` implementation: `POST /api/orders`, order creation (`PENDING`), and `OrderCreatedEvent` Kafka publishing. | Day 2 | **VERIFIED** | Order creation, gateway authentication context, MySQL durability, Kafka publishing with correlationId, customer ownership, and 26 automated unit tests verified. |
| **M9** | `inventory-service` implementation: Stock check, stock reservation, publishing `InventoryReserved` or `InventoryRejected`. | Day 2 | **VERIFIED** | Stock check & reservation, pessimistic write locking, deadlock prevention, idempotent event processing, compensation rollback on PaymentFailed, and 21 automated tests verified. |
| **M10** | `payment-service` implementation: Deterministic mock payment gateway, publishing `PaymentSucceeded` or `PaymentFailed`. | Day 2 | **VERIFIED** | Deterministic MockPaymentGateway, idempotency protection via database-level uniqueness, Kafka publishing within `@Transactional`, compensation release in Inventory verified live, 22 automated tests passing. |
| **M11** | Full Saga Choreography wiring & Compensation rollback (Stock release on payment failure). | Day 2 | **VERIFIED** | All 5 saga scenarios verified live: Scenario A (Happy path -> CONFIRMED), Scenario B (Payment decline -> CANCELLED + stock released), Scenario C (Out-of-stock -> CANCELLED), Scenario D (Duplicate event idempotency), Scenario E (Late event terminal state protection). 37 unit tests passing. |
| **M12** | `notification-service`: Kafka listeners for all status events, audit logging, and authorization-guarded timeline API. | Day 2 | **VERIFIED** | All 5 event types consumed, database idempotency verified, Scenarios A through G passed live in PowerShell (`scratch/test_m12_notifications.ps1`), customer ownership & admin authorization verified, 24 unit & controller tests passing. |
| **M13** | Frontend Cart, Checkout, Simulated Payment, and Live Order Stepper Tracking integration. | Day 2 | **VERIFIED** | Dark navy UI implemented (Cart, Checkout, OrderConfirmation, OrderTracking, OrderHistory), 10 Cart unit tests pass, Vite production build clean (2125 modules), live Flows A through F verified via Gateway (`scratch/test_m13_frontend.ps1`). |
| **M14** | **Day 2 Milestone Checkpoint**: Complete Integration, Security & Reliability Audit. | Day 2 | **VERIFIED** | Monorepo `mvn test` (165 tests passed across all 8 modules, 0 failures), Gateway anti-spoofing verified, Fail-closed Authoritative Catalog Price validation & Client Price Independence implemented/tested (HTTP 503 on catalog outage, strict active check), DB failure Kafka non-acknowledgement verified, Frontend `npm test` (10/10) & Vite build (0 errors) verified. |
| **M15** | UI Polish, Customer Order History, Admin Product Management, and Admin Order Oversight. | Day 3 | **VERIFIED** | Steps 1 through 6 complete. Full Admin Dashboard suite implemented: `/admin` Overview, `/admin/products` Catalog CRUD with modal validation & soft deletion, `/admin/orders` Cross-customer oversight & evidence-backed Saga state machine visualizer, and `/admin/inventory` Real-time SKU tracking with zero-trust atomic stock replenishment (`POST /api/inventory/{productId}/replenish` with pessimistic write locking and JJWT verification). 50 automated frontend unit tests passing (10 Cart + 10 Admin Auth + 10 Admin Product Management + 10 Admin Order Oversight + 10 Admin Inventory); Monorepo `mvn test` 100% clean (184 tests passing across all 8 modules, 0 failures); Vite production build 100% clean (2140 modules). |
| **M16.1** | Global API Error Handling & Sanitization: Centralized `@RestControllerAdvice` across all 6 microservices & API Gateway, standardized `ErrorResponse` record, Jakarta Bean validation mapping, custom exception hierarchy, and sanitized 500 internal error shielding. | Day 3 | **VERIFIED** | Monorepo `mvn test`: 203/203 tests passed across all 8 modules; Frontend `npm test`: 50/50 tests passed cleanly. |
| **M16.2** | Basic Application Logging: SLF4J structured logging configured across all 7 microservices (`application.yml` standardized at `INFO` for business, `WARN` for external clients/Kafka), contextual controller logging without exposing sensitive data (passwords, JWTs, card details). | Day 3 | **VERIFIED** | Logging blocks standardized across all services; structured log output empirically verified during integration tests. |
| **M16.3** | Basic Actuator Health Checks: Standard `spring-boot-starter-actuator` configured on all 7 microservices, exposing `/actuator/health` and `/actuator/info` with detail view enabled, unconditional Gateway routing and security filter bypass. | Day 3 | **VERIFIED** | Monorepo `mvn test` 204/204 passing (including `shouldBypassFilterForActuatorHealth`); Actuator health endpoints return `UP` (HTTP 200). |
| **M16.4** | Docker Compose Verification & Multi-Stage Dockerfiles: Verified Docker container infrastructure on isolated ports (MySQL 3309, Redis 6380, Kafka 9094), added container ping healthcheck, and created multi-stage Dockerfiles across all 7 Java services and Vite React frontend. | Day 3 | **VERIFIED** | Infrastructure containers `Up (healthy)`; Dockerfiles created; zero port collision with host or other local projects. |
| **M16.5** | Basic GitHub Actions CI Workflow: Simple, robust `.github/workflows/ci.yml` building on push and pull requests to `main` and `master`, verifying Java 21 Temurin Maven build/tests and Node 20 frontend tests/production build. | Day 3 | **VERIFIED** | CI workflow file validated; local Maven clean test (204/204) and frontend test (50/50) and build (2140 modules) 100% clean. |
| **M16.6** | Final Customer, Admin, Order, Payment, and Inventory Integration Testing: Comprehensive end-to-end verification covering Customer flow (register, profile, browse, place order, history), Admin flow (login, product create/delete, order oversight, stock replenish), Security (401 unauthorized, 403 forbidden), and Error Handling (400 validation, 404 not found, 409 conflict). | Day 3 | **VERIFIED** | All 15 integration test scenarios passed live in PowerShell (`scratch/test_m16_final_integration.ps1`: 15 passed, 0 failed). Full monorepo backend (204 tests) and frontend (50 tests) passing. |
| **M17** | Swagger / OpenAPI 3 documentation integrated and verified across all services. | Day 3 | PLANNED | Verify Swagger UI at `http://localhost:8080/swagger-ui.html` or direct ports. |
| **M18** | Complete Docker Compose end-to-end build and clean startup verification. | Day 3 | PLANNED | `docker compose down -v; docker compose up --build -d` verified. |
| **M19** | Git repository initialization, commit history review, and push to GitHub repository. | Day 3 | PLANNED | `git status`, commit, push to verified remote GitHub repository. |
| **M20** | **Day 3 Final Checkpoint**: Complete interview defense preparation and architecture review. | Day 3 | PLANNED | Live walkthrough of architecture, interview Q&A cheatsheet ready. |

---

## 3. Detailed Day-by-Day Execution & Testing Protocol

### Day 1: Foundation and Product Catalogue
- **Target Goal**: A working product catalog microservice backed by MySQL and Redis cache, routed through API Gateway, and displayed on a sleek React storefront.
- **Verification Commands (PowerShell)**:
  ```powershell
  # 1. Start Infrastructure
  docker compose up -d ecom-mysql ecom-redis ecom-kafka
  
  # 2. Build and run Product Service
  cd d:\EventDrivenEcommerce\product-service
  mvn clean package -DskipTests
  mvn spring-boot:run
  
  # 3. Test API & Cache
  Invoke-RestMethod -Uri "http://localhost:8081/api/products" -Method GET
  
  # 4. Run Frontend
  cd d:\EventDrivenEcommerce\frontend
  npm install
  npm run dev
  ```

### Day 2: Orders, Kafka Choreography & Authentication
- **Target Goal**: Complete distributed purchase workflow. Order creation publishes `OrderCreatedEvent` -> Inventory reserves stock -> Payment processes -> Order confirms. If payment fails, inventory is automatically restored.
- **Verification Commands (PowerShell)**:
  ```powershell
  # 1. Test User Registration & Login
  $loginRes = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -Body '{"email":"customer@test.com","password":"Password123!"}' -ContentType "application/json"
  $token = $loginRes.data.token
  
  # 2. Test Order Placement (Success Flow)
  $orderRes = Invoke-RestMethod -Uri "http://localhost:8080/api/orders" -Method POST -Headers @{ Authorization = "Bearer $token" } -Body '{"items":[{"productId":1,"quantity":2}],"paymentSimulateMode":"SUCCESS"}' -ContentType "application/json"
  $orderId = $orderRes.data.orderId
  
  # 3. Poll Order Status (Transitions to CONFIRMED)
  Start-Sleep -Seconds 3
  Invoke-RestMethod -Uri "http://localhost:8080/api/orders/$orderId" -Method GET -Headers @{ Authorization = "Bearer $token" }
  
  # 4. Test Failure Compensation Flow
  $failRes = Invoke-RestMethod -Uri "http://localhost:8080/api/orders" -Method POST -Headers @{ Authorization = "Bearer $token" } -Body '{"items":[{"productId":1,"quantity":1}],"paymentSimulateMode":"FAILURE"}' -ContentType "application/json"
  $failOrderId = $failRes.data.orderId
  Start-Sleep -Seconds 3
  Invoke-RestMethod -Uri "http://localhost:8080/api/orders/$failOrderId" -Method GET -Headers @{ Authorization = "Bearer $token" }
  # Verify status == CANCELLED and stock restored
  ```

### Day 3: Quality, UI Polish & GitHub
- **Target Goal**: Enterprise-grade polish, automated tests passing, comprehensive Swagger documentation, and a clean push to the user's GitHub repository.
- **Verification Commands (PowerShell)**:
  ```powershell
  # Run all unit and integration tests
  mvn clean test
  
  # Verify Git state
  git status
  git log --oneline
  ```

---

## 4. Port Conflict Resolution Matrix

During system inspection on 2026-10-29, the following local host ports were detected:
- Host PID 7172 (`mysqld.exe`) listening on port `3306`.
- Existing Docker containers listening on `3307` (`adaptive-mysql`), `3308` (`interviewly-mysql`), `6379` (`adaptive-redis`), and `9092` (`adaptive-kafka`).

**Resolution Table**:
| Service | Container Internal Port | Default Conflicted Host Port | Project Assigned Host Port | Override Variable in `.env` |
| :--- | :---: | :---: | :---: | :--- |
| **MySQL (Project)** | `3306` | `3306` | **`3309`** | `MYSQL_HOST_PORT=3309` |
| **Redis (Project)** | `6379` | `6379` | **`6380`** | `REDIS_HOST_PORT=6380` |
| **Kafka (Project)** | `29092` (internal) | `9092` | **`9094`** | `KAFKA_HOST_PORT=9094` |
| **API Gateway** | `8080` | None | **`8080`** | `GATEWAY_PORT=8080` |
| **Product Service** | `8081` | None | **`8081`** | `PRODUCT_SERVICE_PORT=8081` |
| **Order Service** | `8082` | None | **`8082`** | `ORDER_SERVICE_PORT=8082` |
| **Inventory Service**| `8083` | None | **`8083`** | `INVENTORY_SERVICE_PORT=8083`|
| **Payment Service** | `8084` | None | **`8084`** | `PAYMENT_SERVICE_PORT=8084` |
| **Notification Svc** | `8085` | None | **`8085`** | `NOTIFICATION_SERVICE_PORT=8085` |
| **Auth Service** | `8086` | None | **`8086`** | `AUTH_SERVICE_PORT=8086` |
| **Frontend (Vite)** | `5173` | None | **`5173`** | `FRONTEND_PORT=5173` |
