package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Item request for order creation.
 * Only productId and quantity are required from the client.
 * Authoritative unitPrice, productName, and productSku are retrieved from the Product Service.
 */
public record CreateOrderItemRequest(
        @NotNull(message = "Product ID is required")
        @Positive(message = "Product ID must be positive")
        Long productId,

        String productName,

        String productSku,

        BigDecimal unitPrice,

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be at least 1")
        Integer quantity
) {
}
