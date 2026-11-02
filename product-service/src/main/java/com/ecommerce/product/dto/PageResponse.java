package com.ecommerce.product.dto;

import java.io.Serializable;
import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean last
) implements Serializable {
}
