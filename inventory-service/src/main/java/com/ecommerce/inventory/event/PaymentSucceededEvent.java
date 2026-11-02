package com.ecommerce.inventory.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentSucceededEvent(
        String eventId,
        String correlationId,
        String eventType,
        Long orderId,
        Long paymentId,
        String transactionReference,
        BigDecimal amount,
        Instant timestamp
) {
    public PaymentSucceededEvent(
            String eventId,
            String eventType,
            Long orderId,
            Long paymentId,
            String transactionReference,
            BigDecimal amount,
            Instant timestamp
    ) {
        this(eventId, eventId, eventType, orderId, paymentId, transactionReference, amount, timestamp);
    }
}
