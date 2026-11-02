package com.ecommerce.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "notification_logs", indexes = {
        @Index(name = "idx_order_id", columnList = "orderId"),
        @Index(name = "idx_customer_id", columnList = "customerId")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_event_id", columnNames = {"eventId"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "recipient_email", length = 128)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "RECORDED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = "RECORDED";
        }
    }
}
