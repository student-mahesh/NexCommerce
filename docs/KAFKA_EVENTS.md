# KAFKA_EVENTS.md — Event Choreography Contracts & Idempotency Specification

## 1. Saga Choreography Overview

This architecture implements a distributed transaction for the checkout process across three independent services:
`order-service` -> `inventory-service` -> `payment-service`.

```
[order-service]                  [inventory-service]                  [payment-service]
       |                                  |                                   |
       |--- OrderCreatedEvent ----------->|                                   |
       |                                  |--- InventoryReservedEvent ------->|
       |                                  |                                   |--- PaymentSucceededEvent ---> [order-service: CONFIRMED]
       |                                  |                                   |
       |                                  |<-- PaymentFailedEvent ------------|
       |                                  |    (Triggers Stock Compensation)
       |<-- InventoryRejectedEvent -------|
       |    or PaymentFailedEvent
       |    (Order transitions to CANCELLED)
```

---

## 2. Idempotency & De-duplication Protocol

In distributed systems, network partitions or consumer crashes can cause Apache Kafka to deliver a message more than once (At-Least-Once delivery). 

### Guardrails to Prevent Double Processing
1. **Idempotency in Order Service**:
   - Before applying a status transition, `order-service` evaluates the current state.
   - If order is already in terminal state (`CONFIRMED` or `CANCELLED`), subsequent `PaymentSucceeded` or `PaymentFailed` events are safely ignored and logged as duplicates.
2. **Idempotency in Inventory Service (Reservation & Compensation Protection)**:
   - **Requirement**: *A stock reservation must not be reserved twice or released more than once.*
   - **Solution**: `inventory-service` maintains a `stock_reservations` record per `orderId` with a status enum (`RESERVED`, `RELEASED`, `FULFILLED`, `REJECTED`).
   - When an `OrderCreatedEvent` arrives:
     - Check `reservationRepository.existsByOrderId(orderId)`. If `true`, the order was already evaluated (reserved or rejected) — discard duplicate event immediately.
     - If rejected due to stockout, write a `REJECTED` reservation record to ensure future duplicates are dropped.
   - When a `PaymentFailedEvent` arrives:
     - Check `reservation.getStatus()`.
     - If `RELEASED`, `FULFILLED`, or `REJECTED`, the event is a duplicate or inapplicable: do nothing, do not increment available stock again!
     - If `RESERVED`, restore available stock, decrement reserved stock, transition status to `RELEASED`, and emit `InventoryReleasedEvent`.
3. **Pessimistic Row Locking & Deadlock Prevention**:
   - Spring Data JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`) acquires exclusive row locks on `inventory_items` during stock checks and reservations.
   - **Deadlock Prevention**: Product IDs in multi-item orders are sorted ascending before acquiring row locks, ensuring a uniform lock acquisition sequence across concurrent order threads.
4. **Optimistic Locking**:
   - JPA `@Version` column on `inventory_items` guards against concurrent out-of-band updates.
5. **Decoupled Deserialization Architecture**:
   - Consumers use `StringDeserializer` with Jackson `ObjectMapper` and `@JsonIgnoreProperties(ignoreUnknown = true)`.
   - Bypasses strict Java package name coupling in Kafka `__TypeId__` headers, preventing `ClassNotFoundException` between microservices while allowing graceful schema evolution.
6. **Idempotency in Notification Service (Unique Event Constraint)**:
   - `notification-service` persists incoming notifications in `notification_logs` with a unique database constraint on `event_id` (`uk_notification_event_id`).
   - If an event is re-delivered by Kafka, `notificationRepository.existsByEventId` immediately drops the duplicate. Any concurrent race conditions are trapped by `DataIntegrityViolationException`, ensuring that customer notification audit trails never contain duplicate rows.
7. **Reliable Manual Offset Acknowledgment Under DB Failure (M14 Audit)**:
   - In `NotificationEventListener`, `ack.acknowledge()` is called strictly after `notificationService.recordNotification(...)` succeeds.
   - If MySQL throws a transient `DataAccessException`, the exception propagates uncaught to Spring Kafka, ensuring the partition offset is not committed and the event is redelivered.
   - Truly unrecoverable poison pills (`JacksonException` on corrupted JSON) are caught, logged, and acknowledged to avoid blocking partition throughput.

---

## 3. Kafka Topics & Event Payloads

### Topic: `order-created-events`
- **Key**: `orderId` (Long converted to String)
- **Producer**: `order-service`
- **Consumer**: `inventory-service`, `notification-service`
- **Compatibility**: Annotated with `@JsonIgnoreProperties(ignoreUnknown = true)`.
```json
{
  "eventId": "a5cfe6c7-a3b4-4f13-9bba-4129bfec25b9",
  "correlationId": "CORR-1-fc22c699",
  "eventType": "ORDER_CREATED",
  "orderId": 1,
  "customerId": 1,
  "customerEmail": "customer@test.com",
  "totalAmount": 309.97,
  "paymentSimulateMode": "SUCCESS",
  "items": [
    {
      "productId": 1,
      "sku": "TECH-KB-01",
      "quantity": 2,
      "unitPrice": 89.99
    },
    {
      "productId": 2,
      "sku": "AUD-HP-02",
      "quantity": 1,
      "unitPrice": 129.99
    }
  ],
  "timestamp": "2026-10-30T10:00:00Z"
}
```

### Topic: `inventory-reserved-events`
- **Key**: `orderId`
- **Producer**: `inventory-service`
- **Consumer**: `payment-service`, `notification-service`
```json
{
  "eventId": "f4b00982-f72b-4ec9-bd99-73ba5162f22e",
  "correlationId": "corr-7001",
  "eventType": "INVENTORY_RESERVED",
  "orderId": 7001,
  "customerId": 1,
  "customerEmail": "customer@test.com",
  "totalAmount": 599.97,
  "paymentSimulateMode": "SUCCESS",
  "reservedItems": [
    {
      "productId": 1,
      "quantity": 3
    }
  ],
  "timestamp": "2026-10-30T12:30:00Z"
}
```

### Topic: `inventory-rejected-events`
- **Key**: `orderId`
- **Producer**: `inventory-service`
- **Consumer**: `order-service`, `notification-service`
```json
{
  "eventId": "7a8c3d19-ef82-411a-82dc-304b8d7162fa",
  "correlationId": "corr-7002",
  "eventType": "INVENTORY_REJECTED",
  "orderId": 7002,
  "reason": "Insufficient stock for product ID 1 (available: 34, requested: 9999)",
  "timestamp": "2026-10-30T12:35:00Z"
}
```

### Topic: `payment-succeeded-events`
- **Key**: `orderId`
- **Producer**: `payment-service`
- **Consumer**: `order-service` (state -> CONFIRMED), `inventory-service` (stock fulfillment), `notification-service`
- **Simulation Note**: Generated deterministically by `MockPaymentGateway` when `paymentSimulateMode` is `SUCCESS`. No real banking or card credentials are processed.
```json
{
  "eventId": "18cf3291-5801-443b-8ea9-42b87b7a2f1c",
  "correlationId": "corr-7001",
  "eventType": "PaymentSucceededEvent",
  "orderId": 7001,
  "paymentId": 501,
  "transactionReference": "TXN-AUTH-630556c7-ba25-45a9-ad7a-98936a7faf1f",
  "amount": 599.97,
  "timestamp": "2026-10-30T12:35:00Z"
}
```

### Topic: `payment-failed-events`
- **Key**: `orderId`
- **Producer**: `payment-service`
- **Consumer**: `order-service` (state -> CANCELLED), `inventory-service` (triggers stock release compensation), `notification-service`
- **Simulation Note**: Generated deterministically by `MockPaymentGateway` when `paymentSimulateMode` is `FAILURE`.
```json
{
  "eventId": "e6a10c77-44bc-4672-bdc4-6c703b41829e",
  "correlationId": "corr-7001",
  "eventType": "PaymentFailedEvent",
  "orderId": 7001,
  "reason": "Simulated card decline: insufficient test funds or card issuer decline",
  "timestamp": "2026-10-30T12:40:00Z"
}
```

### Topic: `inventory-released-events`
- **Key**: `orderId`
- **Producer**: `inventory-service`
- **Consumer**: `notification-service`
```json
{
  "eventId": "62bf19aa-782b-4573-9a84-632b7194cc31",
  "correlationId": "corr-7001",
  "eventType": "INVENTORY_RELEASED",
  "orderId": 7001,
  "releasedItems": [
    {
      "productId": 1,
      "quantity": 3
    }
  ],
  "timestamp": "2026-10-30T12:40:00Z"
}
```
