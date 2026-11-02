package com.ecommerce.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA Entity representing a simulated payment transaction in ecommerce_payment.
 * Never stores real card numbers, CVVs, or financial credentials.
 */
@Entity
@Table(
        name = "payment_transactions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_order_id", columnNames = {"order_id"})
        },
        indexes = {
                @Index(name = "idx_payment_order_id", columnList = "order_id", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_method", nullable = false, length = 32)
    @Builder.Default
    private String paymentMethod = "CARD_DEMO";

    @Column(name = "payment_simulate_mode", nullable = false, length = 32)
    private String paymentSimulateMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "transaction_reference", nullable = false, length = 64)
    private String transactionReference;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "demo_notice", length = 128)
    @Builder.Default
    private String demoNotice = "DEMO/SIMULATED PAYMENT ONLY";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.paymentMethod == null) {
            this.paymentMethod = "CARD_DEMO";
        }
        if (this.demoNotice == null) {
            this.demoNotice = "DEMO/SIMULATED PAYMENT ONLY";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
