package com.ecommerce.order.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedEvent(
        String eventId,
        String correlationId,
        String eventType,
        Long orderId,
        Long customerId,
        String customerEmail,
        BigDecimal totalAmount,
        String paymentSimulateMode,
        List<OrderItemPayload> items,
        Instant timestamp
) {
    public OrderCreatedEvent(
            String eventId,
            String eventType,
            Long orderId,
            Long customerId,
            String customerEmail,
            BigDecimal totalAmount,
            String paymentSimulateMode,
            List<OrderItemPayload> items,
            Instant timestamp
    ) {
        this(eventId, eventId, eventType, orderId, customerId, customerEmail, totalAmount, paymentSimulateMode, items, timestamp);
    }

    public record OrderItemPayload(
            Long productId,
            String sku,
            Integer quantity,
            BigDecimal unitPrice
    ) {}
}
