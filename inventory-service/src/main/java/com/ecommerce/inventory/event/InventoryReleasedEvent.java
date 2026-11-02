package com.ecommerce.inventory.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryReleasedEvent(
        String eventId,
        String correlationId,
        String eventType,
        Long orderId,
        List<InventoryReservedEvent.ReservedItemPayload> releasedItems,
        Instant timestamp
) {
    public InventoryReleasedEvent(
            String eventId,
            String eventType,
            Long orderId,
            List<InventoryReservedEvent.ReservedItemPayload> releasedItems,
            Instant timestamp
    ) {
        this(eventId, eventId, eventType, orderId, releasedItems, timestamp);
    }
}
