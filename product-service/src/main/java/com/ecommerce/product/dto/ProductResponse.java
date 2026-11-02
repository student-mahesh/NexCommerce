package com.ecommerce.product.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String sku,
        String imageUrl,
        String category,
        Integer stockQuantity,
        Boolean active,
        Instant createdAt
) implements Serializable {
}
