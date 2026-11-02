package com.ecommerce.product.dto;

import java.io.Serializable;

public record CategoryResponse(
        Long id,
        String name,
        String description,
        Long productCount
) implements Serializable {
}
