# AGENTS.md — Development Guardrails & Mentoring Protocol

This document defines the persistent engineering standards, architectural rules, code quality guidelines, and mentoring protocol for the **Event-Driven E-Commerce Microservices** project. Every automated agent and contributing developer must strictly adhere to these guidelines.

---

## 1. Core Engineering Persona & Mission

- **Role**: Senior Java Full-Stack Engineer, Software Architect, UI/UX Designer, and Mentor.
- **Target Audience**: Fresher / Junior Developer preparing for Java Full-Stack Developer technical interviews.
- **Guiding Principle**: **Simplicity, Correctness, and Defensibility over accidental complexity.**
  - Code must be clean, readable, well-commented, and easy to explain during a technical interview.
  - Avoid overengineering, esoteric design patterns, huge monolithic methods, duplicated logic, and magic strings.
  - Prefer standard Spring Boot conventions (`@RestController`, `@Service`, `@Repository`, Spring Data JPA, KafkaTemplate, `@KafkaListener`).

---

## 2. Mandatory Beginner-Friendly Mentoring Protocol

For every implementation milestone, the agent must follow this 10-step protocol:

1. **Business Purpose in Plain English**: Explain what the feature does, what real-world e-commerce problem it solves, and how it fits into the customer journey.
2. **Why the Technology is Chosen**: Explain why Java 21, Spring Boot, Kafka, Redis, MySQL, React, Redux, or Docker is the right tool for this specific milestone.
3. **File Blueprint**: List every file that will be created or modified before touching code.
4. **Focused Implementation**: Implement only the approved milestone. Do not skip ahead or generate unrelated code.
5. **Deep Code Explanation**: Explain key classes, annotations (`@KafkaListener`, `@Cacheable`, `@PreAuthorize`, etc.), records, methods, and the runtime data flow.
6. **Exact Windows PowerShell Commands**: Provide copy-paste-ready commands to build, run, and test the code in Windows PowerShell.
7. **Empirical Verification**: Run the tests and commands; report real output. Never claim a test passed without running it.
8. **Root Cause Analysis (RCA) on Errors**: If an error occurs, explain what failed, why it failed, and how the fix resolves the root cause.
9. **Interview Cheatsheet**: Provide a concise 2-minute interview answer and 3 likely follow-up questions with answers for the implemented feature.
10. **Living Progress Tracker**: Update `DEVELOPMENT_PLAN.md` with verified status and test evidence.

---

## 3. Architecture & Microservice Guardrails

### 3.1 Service Boundaries & Database Ownership
- **Database-per-Service Principle**: Each microservice strictly owns its database schema. No microservice may connect to or query another service's database tables directly.
  - `product-service` -> `ecommerce_product`
  - `order-service` -> `ecommerce_order`
  - `inventory-service` -> `ecommerce_inventory`
  - `payment-service` -> `ecommerce_payment`
  - `notification-service` -> `ecommerce_notification`
  - `auth-service` -> `ecommerce_auth`
- **Data Sharing via APIs & Events**:
  - Request/Response queries: REST through `api-gateway`.
  - Asynchronous business processes & state transitions: Apache Kafka events.

### 3.2 Port Allocation & Conflict Prevention
Existing system analysis revealed host MySQL on port `3306` (PID 7172) and existing containers on ports `3307`, `3308`, `6379`, and `9092`. To prevent collisions:
- **API Gateway**: `8080`
- **Product Service**: `8081`
- **Order Service**: `8082`
- **Inventory Service**: `8083`
- **Payment Service**: `8084`
- **Notification Service**: `8085`
- **Auth Service**: `8086`
- **Frontend (Vite)**: `5173` (or `3000`)
- **Docker MySQL**: Container port `3306`, exposed host port `3309` (configurable via `.env`)
- **Docker Redis**: Container port `6379`, exposed host port `6380` (or `6379` if container isolated)
- **Docker Kafka**: Internal broker `kafka:29092`, external host listener `localhost:9094`

### 3.3 Event Choreography & Idempotency Rules
- Event naming must use past-tense domain events:
  - `OrderCreatedEvent`
  - `InventoryReservedEvent`
  - `InventoryRejectedEvent`
  - `PaymentSucceededEvent`
  - `PaymentFailedEvent`
  - `InventoryReleasedEvent`
- Every event must carry:
  - `eventId` (UUID)
  - `orderId` (Long/UUID)
  - `timestamp` (Instant)
  - `eventType` (String)
  - `payload` (Domain details: items, quantities, price, customer info)
- **Idempotency**: Consumers must guard against duplicate deliveries by checking if the order or transaction has already been processed for that state.

---

## 4. Backend Coding Standards (Java 21 / Spring Boot 3)

- **Language Level**: Java 21. Take advantage of modern, readable Java features:
  - Java Records for immutable DTOs and Kafka Event payloads (`public record OrderCreatedEvent(...) {}`).
  - Pattern matching for `switch` and `instanceof`.
  - Text blocks for multi-line SQL or JSON in tests.
- **REST API Standard**:
  - All REST endpoints must return a consistent wrapper:
    ```java
    public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
    ) {
        public static <T> ApiResponse<T> ok(String message, T data) {
            return new ApiResponse<>(true, message, data, Instant.now());
        }
        public static <T> ApiResponse<T> error(String message) {
            return new ApiResponse<>(false, message, null, Instant.now());
        }
    }
    ```
  - Standard HTTP status codes: `200 OK`, `201 Created`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict`, `500 Internal Server Error`.
  - Global Exception Handler using `@RestControllerAdvice` and `@ExceptionHandler`.
- **Validation**: Use Jakarta Bean Validation (`@NotNull`, `@NotBlank`, `@Positive`, `@Email`) on all incoming request DTOs.
- **Logging**: Use SLF4J (`@Slf4j` or `LoggerFactory.getLogger`) with structured messages including IDs (e.g., `log.info("Processing orderId={}", orderId);`). Never use `System.out.println` in backend microservices.

---

## 5. Security & Authentication Guardrails

- **JWT + Google OAuth2 Hybrid Architecture**:
  1. Frontend initiates Google OAuth2 login or standard Email/Password authentication.
  2. `auth-service` validates credentials or verifies Google OAuth2 authorization code / ID token.
  3. `auth-service` generates a cryptographically signed JWT containing `sub` (userId), `email`, and `roles` (`ROLE_CUSTOMER`, `ROLE_ADMIN`).
  4. Frontend stores the JWT in secure storage (HTTP-only cookie or memory/localStorage for demo) and includes it in the `Authorization: Bearer <token>` header on subsequent requests.
  5. `api-gateway` validates the JWT signature and claims using a reactive Global Authentication Filter, extracts user identity, and injects `X-User-Id`, `X-User-Email`, and `X-User-Roles` HTTP headers for downstream services.
- **Zero Secrets in Source Code**:
  - No database passwords, JWT secrets, or Google OAuth2 client secrets committed to Git.
  - All secrets loaded from environment variables (`.env` file).
  - Safe, descriptive placeholders committed to `.env.example`.

---

## 6. Frontend Conventions (React + Vite + Bootstrap + Tailwind)

- **Role Differentiation for Bootstrap & Tailwind**:
  - **Bootstrap**: Use for foundational UI layout, grid system (`container`, `row`, `col`), standard forms (`form-control`, `form-check`), and accessible components (modals, dropdowns, alerts).
  - **Tailwind CSS**: Use for custom aesthetic refinement, subtle micro-interactions, sleek dark/light card styling, pill badges, gradients, and custom spacing utilities.
  - **Rule**: Do NOT mix competing classes on the same element (e.g., do not apply both Bootstrap `btn btn-primary` and Tailwind `bg-blue-500` to the same button).
- **State Management**:
  - Redux Toolkit for global state: `authSlice` (user session, token), `cartSlice` (items, quantities, totals).
  - React Query or standard Axios hooks with loading, error, and empty states.
- **UI Quality Standard**:
  - No fake buttons or non-functional placeholder links.
  - Clear user feedback: Spinners on submit buttons, toast notifications on success/error, confirmation modals for destructive actions.
  - Responsive layout that looks polished on mobile, tablet, and desktop.

---

## 7. Testing & Verification Rules

- **Unit Testing**: JUnit 5 + Mockito for business services and controllers.
- **Integration Testing**: Test Kafka listeners with `@EmbeddedKafka` or containerized Kafka, and Redis caching tests.
- **Verification Command Rule**: Every code change must be accompanied by its execution command and verification output.
- **No Fictitious Test Results**: If a test fails, state the exact stack trace and fix it before reporting milestone completion.
