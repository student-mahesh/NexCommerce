package com.ecommerce.payment.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentFailedEvent(
        String eventId,
        String correlationId,
        String eventType,
        Long orderId,
        String reason,
        Instant timestamp
) {
    public PaymentFailedEvent(
            String eventId,
            String eventType,
            Long orderId,
            String reason,
            Instant timestamp
    ) {
        this(eventId, eventId, eventType, orderId, reason, timestamp);
    }
}
