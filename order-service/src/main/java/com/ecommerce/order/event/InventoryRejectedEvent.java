package com.ecommerce.order.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryRejectedEvent(
        String eventId,
        String correlationId,
        String eventType,
        Long orderId,
        String reason,
        Instant timestamp
) {
    public InventoryRejectedEvent(
            String eventId,
            String eventType,
            Long orderId,
            String reason,
            Instant timestamp
    ) {
        this(eventId, eventId, eventType, orderId, reason, timestamp);
    }
}
