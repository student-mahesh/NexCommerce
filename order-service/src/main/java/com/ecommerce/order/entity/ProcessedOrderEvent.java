package com.ecommerce.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA Entity recording processed Kafka outcome events in ecommerce_order.
 * Enforces database-level uniqueness across (order_id, event_type) to guarantee
 * durable idempotency against duplicate deliveries and concurrent race conditions.
 */
@Entity
@Table(
        name = "order_processed_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_order_event_type", columnNames = {"order_id", "event_type"})
        },
        indexes = {
                @Index(name = "idx_processed_event_order", columnList = "order_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedOrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @PrePersist
    protected void onCreate() {
        if (this.processedAt == null) {
            this.processedAt = Instant.now();
        }
    }
}

