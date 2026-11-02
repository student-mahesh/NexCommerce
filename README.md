# NexCommerce — Event-Driven E-Commerce Microservices

A production-grade, interview-ready distributed e-commerce application built with **Java 21**, **Spring Boot 3**, **Spring Cloud Gateway**, **Apache Kafka**, **Redis**, **MySQL**, and **React 18 (Vite + Redux Toolkit + Bootstrap 5 + Tailwind CSS)**.

This system demonstrates the **Choreography-based Saga Pattern** for distributed transactions, eventual consistency, automated compensation (stock release on payment failure), sub-millisecond catalog caching, hybrid **Google OAuth2 + JWT** security, and a complete **Admin Management Dashboard**.

---

## 1. Architectural Highlights & Features

- **Event-Driven Choreography Saga**: Eliminates synchronous HTTP coupling between order placement, inventory reservation, and payment processing.
- **Automated Compensation**: If a payment fails after stock is reserved, an asynchronous compensation event releases the reserved inventory and cancels the order.
- **Cache-Aside Pattern with Redis**: High-read product catalog operations achieve sub-20ms response times with automated cache eviction on product updates.
- **Hybrid Security**: Google OAuth2 authentication seamlessly integrated with self-contained, cryptographically signed platform JWTs forwarded through Spring Cloud Gateway. Downstream microservices enforce defense-in-depth authorization.
- **Database-per-Service**: Six decoupled MySQL database schemas (`ecommerce_auth`, `ecommerce_product`, `ecommerce_order`, `ecommerce_inventory`, `ecommerce_payment`, `ecommerce_notification`) guaranteeing strict domain boundary isolation.
- **Standardized API Error Handling**: Centralized `@RestControllerAdvice` across all services returning consistent, sanitized `ErrorResponse` objects with field-level validation errors (no raw stack traces exposed).
- **Observability & Health Checks**: Standard Spring Boot Actuator `/actuator/health` and `/actuator/info` endpoints with SLF4J structured operational logging.
- **Admin Dashboard Suite**: Dedicated administrative interface at `/admin` featuring catalog CRUD with image previews and soft deletion, cross-customer order oversight with live Saga visualizer, and real-time inventory management with concurrency-safe stock replenishment.
- **Modern Java 21**: Leverages Java records for immutable domain events and DTOs, pattern matching for switch/instanceof, and text blocks.

---

## 2. Technology Stack

| Domain | Technology | Purpose |
| :--- | :--- | :--- |
| **Language & Runtime** | Java 21 LTS (Temurin/OpenJDK) | Modern backend runtime (records, pattern matching, virtual threads ready). |
| **Microservice Framework** | Spring Boot 3.3.4 | Independent, production-ready microservice applications. |
| **API Gateway** | Spring Cloud Gateway (Reactive) | Unified client entry point, routing, CORS, JWT authentication filter. |
| **Event Broker** | Apache Kafka 4.1 (KRaft Mode) | Asynchronous domain event distribution & Saga choreography. |
| **Caching Layer** | Redis 8.0 | High-speed cache-aside for product catalog. |
| **Relational Database** | MySQL 8.4 | Relational persistence with isolated schema per microservice. |
| **Data Access** | Spring Data JPA / Hibernate 6.5 | Object-Relational Mapping, pessimistic locking, and repository abstractions. |
| **Security & Auth** | Spring Security 6, JJWT 0.12, OAuth2 | BCrypt password hashing, Google sign-in, signed JWTs. |
| **API Documentation** | Springdoc OpenAPI 3 / Swagger | Interactive REST API documentation and live testing. |
| **Frontend Framework** | React 18, Vite 5 | Single-page application with lightning-fast HMR and bundle optimization. |
| **State Management** | Redux Toolkit | Centralized authentication and shopping cart state with local persistence. |
| **Styling System** | Bootstrap 5.3 + Tailwind CSS 3.4 | Harmonious grid layouts, forms, modern dark/light cards. |
| **Testing** | JUnit 5, Mockito, Vitest | Automated unit, service, and frontend testing (204 backend + 50 frontend tests). |
| **CI / CD** | GitHub Actions | Automated build, test, and package pipeline on push and pull requests. |
| **Containerization** | Docker, Docker Compose | Multi-stage Dockerfiles and reproducible multi-container local infrastructure. |

---

## 3. System Architecture & Port Map

```
 +-------------------------------------------------------------------------------+
 |                              REACT FRONTEND                                   |
 |                       http://localhost:5173 (Vite)                            |
 +---------------------------------------+---------------------------------------+
                                         | HTTP (JWT Bearer Token)
                                         v
 +-------------------------------------------------------------------------------+
 |                         SPRING CLOUD API GATEWAY                              |
 |                         http://localhost:8080                                 |
 +-------+------------+------------+-------------+---------------+---------------+
         |            |            |             |               |               |
         v            v            v             v               v               v
    +---------+  +---------+  +---------+   +---------+     +---------+     +---------+
    |  auth   |  | product |  |  order  |   |inventory|     | payment |     |notificat|
    | service |  | service |  | service |   | service |     | service |     | service |
    |  :8086  |  |  :8081  |  |  :8082  |   |  :8083  |     |  :8084  |     |  :8085  |
    +----+----+  +----+----+  +----+----+   +----+----+     +----+----+     +----+----+
         |            |            |             |               |               |
       MySQL        MySQL        MySQL         MySQL           MySQL           MySQL
    (Host: 3309) (Host: 3309) (Host: 3309)  (Host: 3309)   (Host: 3309)   (Host: 3309)
                      |
                    Redis
                 (Host: 6380)
```

### Port Allocation & Conflict Prevention
The host ports are explicitly configured to prevent collisions with existing system installations:
- **API Gateway**: `8080`
- **Product Service**: `8081`
- **Order Service**: `8082`
- **Inventory Service**: `8083`
- **Payment Service**: `8084`
- **Notification Service**: `8085`
- **Auth Service**: `8086`
- **Frontend**: `5173`
- **Docker MySQL**: Internal `3306` -> Host **`3309`**
- **Docker Redis**: Internal `6379` -> Host **`6380`**
- **Docker Kafka**: Internal `29092` -> Host **`9094`**

---

## 4. Repository Directory Structure

```
EventDrivenEcommerce/
├── .github/
│   └── workflows/
│       └── ci.yml                 # GitHub Actions CI workflow (Java 21 + Node 20)
├── api-gateway/                   # Spring Cloud Gateway (Port 8080)
│   ├── Dockerfile
│   └── src/
├── auth-service/                  # Authentication & OAuth2 Service (Port 8086)
│   ├── Dockerfile
│   └── src/
├── product-service/               # Product Catalog & Redis Caching (Port 8081)
│   ├── Dockerfile
│   └── src/
├── order-service/                 # Order Lifecycle & Saga Coordinator (Port 8082)
│   ├── Dockerfile
│   └── src/
├── inventory-service/             # Inventory Reservation & Replenishment (Port 8083)
│   ├── Dockerfile
│   └── src/
├── payment-service/               # Deterministic Mock Payment Engine (Port 8084)
│   ├── Dockerfile
│   └── src/
├── notification-service/          # Event Audit Logging & Customer Timeline (Port 8085)
│   ├── Dockerfile
│   └── src/
├── frontend/                      # React 18 + Vite Storefront & Admin App (Port 5173)
│   ├── Dockerfile
│   └── src/
├── infrastructure/
│   └── mysql/init/                # Multi-database initialization scripts
├── scratch/                       # Automated PowerShell verification scripts
├── docker-compose.yml             # Local infrastructure (MySQL, Redis, Kafka)
├── pom.xml                        # Root Maven multi-module parent POM
├── .env.example                   # Environment configuration template
├── ARCHITECTURE.md                # In-depth architectural design & sequence diagrams
├── DEVELOPMENT_PLAN.md            # Verified milestone progress tracker
├── PROJECT_SPEC.md                # Specifications and requirements
└── README.md                      # Project documentation
```

---

## 5. Prerequisites

Before starting, ensure the following are installed:
- **Java**: 21 LTS (`java -version`)
- **Maven**: 3.9+ (`mvn -version`)
- **Docker & Docker Desktop**: Docker 24+ (`docker --version`, `docker compose version`)
- **Node.js**: 18+ or 20+ (`node -v`, `npm -v`)
- **Git**: 2.30+ (`git --version`)

---

## 6. Quick Start Guide

### Step 1: Environment Setup
```powershell
cd D:\EventDrivenEcommerce
Copy-Item .env.example .env
```

### Step 2: Start Infrastructure (MySQL, Redis, Kafka)
```powershell
docker compose up -d ecom-mysql ecom-redis ecom-kafka
```
Verify infrastructure health:
```powershell
docker compose ps
```
All three containers should report `Up (healthy)`.

### Step 3: Build & Package Microservices
Run the root Maven build:
```powershell
mvn clean install -DskipTests
```

### Step 4: Run Microservices
Launch the microservices using your IDE or PowerShell:
```powershell
# In separate terminals (or background jobs):
java -Xms64m -Xmx200m -jar api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar
java -Xms64m -Xmx200m -jar auth-service/target/auth-service-1.0.0-SNAPSHOT.jar
java -Xms64m -Xmx200m -jar product-service/target/product-service-1.0.0-SNAPSHOT.jar
java -Xms64m -Xmx200m -jar inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar
java -Xms64m -Xmx200m -jar order-service/target/order-service-1.0.0-SNAPSHOT.jar
java -Xms64m -Xmx200m -jar payment-service/target/payment-service-1.0.0-SNAPSHOT.jar
java -Xms64m -Xmx200m -jar notification-service/target/notification-service-1.0.0-SNAPSHOT.jar
```

Verify service health:
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/health"
# Returns: {"status":"UP"}
```

### Step 5: Start Frontend
```powershell
cd frontend
npm install
npm run dev
```
Open **`http://localhost:5173`** in your browser.

---

## 7. Testing Suites

### Backend Unit & Integration Tests (JUnit 5 + Mockito)
Execute the complete 8-module Maven test suite:
```powershell
mvn test
```
**Results**: **204 passed, 0 failures, 0 errors, 0 skipped**.

### Frontend Unit Tests (Vitest)
Execute the frontend test suite:
```powershell
npm test --prefix frontend
```
**Results**: **50 passed, 0 failures** across 5 test suites (Cart, Admin Auth, Admin Products, Admin Orders, Admin Inventory).

### Production Frontend Build
```powershell
npm run build --prefix frontend
```
**Results**: 2,140 modules transformed cleanly without bundle errors.

### Live End-to-End Integration Verification
Run the automated 15-scenario PowerShell verification script:
```powershell
powershell -ExecutionPolicy Bypass -File scratch/test_m16_final_integration.ps1
```
**Scenarios Verified**:
1. Actuator Health Check via API Gateway (`UP`)
2. Customer Registration & Token Issuance (`201 Created`)
3. Customer Profile via Authenticated JWT (`ROLE_CUSTOMER`)
4. Catalog Browsing & Redis Caching (`200 OK`)
5. Order Placement & Event-Driven Saga Initiation (`201 Created`)
6. Customer Order History Isolation (`200 OK`)
7. Admin Login & Authorization (`ROLE_ADMIN`)
8. Admin Product Management (Create & Soft-Delete)
9. Admin Cross-Customer Order Oversight
10. Admin Inventory Oversight & Atomic Stock Replenishment
11. Security Guardrail — 401 Unauthorized on Missing Token
12. Security Guardrail — 403 Forbidden on Role Escalation
13. Error Handling — 400 Bad Request with Structured Field Error Map
14. Error Handling — 404 Not Found on Missing Resource
15. Error Handling — 409 Conflict on Duplicate Registration

---

## 8. Choreography Saga Walkthrough

### Scenario A: Successful Checkout Flow (Happy Path)
1. **Customer places order**: `POST /api/orders` saves order as `PENDING` and emits `OrderCreatedEvent`.
2. **Inventory reservation**: `inventory-service` consumes `OrderCreatedEvent`, validates stock with pessimistic locking, reserves quantity, and emits `InventoryReservedEvent`.
3. **Payment authorization**: `payment-service` consumes `InventoryReservedEvent`, simulates card authorization, and emits `PaymentSucceededEvent`.
4. **Order confirmation**: `order-service` consumes `PaymentSucceededEvent`, updates status to `CONFIRMED`.
5. **Customer notification**: `notification-service` consumes status events and writes audit timeline.
6. **UI update**: Frontend order tracking stepper marks order as `CONFIRMED`.

### Scenario B: Payment Decline & Compensation Flow
1. **Customer places order**: Order created as `PENDING`, `OrderCreatedEvent` published.
2. **Inventory reservation**: Stock reserved, `InventoryReservedEvent` published.
3. **Payment decline**: `payment-service` simulates card decline (e.g. simulation toggle set to `FAILURE`) and emits `PaymentFailedEvent`.
4. **Automated Compensation**:
   - `inventory-service` consumes `PaymentFailedEvent`, releases reserved stock, restores available balance, and emits `InventoryReleasedEvent`.
   - `order-service` consumes `PaymentFailedEvent`, updates order status to `CANCELLED`.
5. **UI update**: Frontend stepper updates order to `CANCELLED` and displays rejection reason.

---

## 9. GitHub Actions CI Pipeline

The project includes an automated continuous integration pipeline defined at [`.github/workflows/ci.yml`](.github/workflows/ci.yml).
- **Triggers**: Runs on every `push` and `pull_request` targeting `main` or `master`.
- **Backend Job**:
  - Environment: `ubuntu-latest`.
  - Java: `21` (`temurin`).
  - Caching: Automatic Maven package cache.
  - Commands: `mvn -B clean compile` and `mvn -B test` (running all 204 unit and integration tests).
- **Frontend Job**:
  - Environment: `ubuntu-latest`.
  - Node.js: `20`.
  - Caching: Automatic `npm` cache.
  - Commands: `npm ci`, `npm test` (all 50 Vitest tests), and `npm run build`.

---

## 10. Security & Defense-in-Depth

- **Token Signature Verification**: HMAC-SHA512 cryptographic signature check on all incoming requests at the API Gateway.
- **Identity Header Sanitization**: The Gateway explicitly strips incoming client-supplied `X-User-Id`, `X-User-Email`, and `X-User-Roles` headers on public endpoints to prevent identity spoofing.
- **Downstream Verification**: Sensitive mutation endpoints (e.g. `/api/inventory/{id}/replenish` and `/api/products` POST/DELETE) independently re-verify the Bearer JWT and the `ROLE_ADMIN` claim.
- **Zero Secrets in Source**: All database credentials, JWT secrets, and OAuth keys are loaded via environment variables; `.gitignore` guards local `.env` files.
- **Sanitized Error Responses**: Internal exception messages, database queries, and stack traces are suppressed in production mode via centralized `@RestControllerAdvice` exception handlers.

---

## 11. Known Limitations & Future Roadmap

- **Simulated Payment Engine**: The payment engine uses a deterministic simulation service instead of a live banking gateway (Stripe/PayPal), ideal for safe offline demonstration and interview review.
- **Single-Broker Kafka**: Configured with a single Kafka broker in KRaft mode for lightweight local developer ergonomics. In multi-node production, a replication factor of 3 would be configured.
- **Email Delivery**: `notification-service` currently records events to an audit table and browser-accessible timeline API; integration with live SendGrid or Amazon SES is planned for a future release.
- **Search Optimization**: Catalog search currently leverages MySQL indices; future iterations can introduce Elasticsearch for full-text fuzzy querying.

---

## 12. Interview Defense Cheatsheet

### Q1: Why did you choose Choreography Saga over Orchestration Saga?
> "In an Orchestration Saga, a central coordinator sends commands to participants. While this provides centralized state visibility, it introduces tight coupling, single point of failure risk, and orchestration bottlenecks.
> In our e-commerce flow, **Choreography** is simpler, more resilient, and loosely coupled: each service listens to Kafka domain events (`OrderCreatedEvent`, `InventoryReservedEvent`, `PaymentFailedEvent`) and autonomously executes its state transition. Services scale, deploy, and evolve independently."

### Q2: How do you prevent race conditions during concurrent inventory reservations?
> "In `inventory-service`, we use **Pessimistic Write Locking** (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on the MySQL inventory row during stock checks. When an order arrives, the database places an exclusive row-level lock (`SELECT ... FOR UPDATE`), preventing concurrent transactions from reading or modifying the stock quantity until the reservation commits. This eliminates double-selling and race conditions."

### Q3: Why combine Google OAuth2 with self-contained JWTs?
> "Google OAuth2 is an authorization delegation protocol that lets users authenticate securely without storing third-party credentials. However, validating a Google OAuth2 token on every internal microservice request would introduce 200ms+ external network latency and external rate limit risks.
> By exchanging the validated Google token for our own platform JWT in `auth-service`, our API Gateway verifies signatures locally in sub-millisecond time and injects authenticated identity headers downstream."
