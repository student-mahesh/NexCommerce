package com.ecommerce.inventory.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryReservedEvent(
        String eventId,
        String correlationId,
        String eventType,
        Long orderId,
        Long customerId,
        String customerEmail,
        BigDecimal totalAmount,
        String paymentSimulateMode,
        List<ReservedItemPayload> reservedItems,
        Instant timestamp
) {
    public InventoryReservedEvent(
            String eventId,
            String eventType,
            Long orderId,
            Long customerId,
            String customerEmail,
            BigDecimal totalAmount,
            String paymentSimulateMode,
            List<ReservedItemPayload> reservedItems,
            Instant timestamp
    ) {
        this(eventId, eventId, eventType, orderId, customerId, customerEmail, totalAmount, paymentSimulateMode, reservedItems, timestamp);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReservedItemPayload(
            Long productId,
            Integer quantity
    ) {}
}
