package com.ecommerce.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for restocking / replenishing available inventory")
public class ReplenishmentRequest {

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Replenishment quantity must be at least 1")
    @Max(value = 10000, message = "Replenishment quantity cannot exceed 10,000 per request")
    @Schema(description = "Number of units to add to available stock", example = "50")
    private Integer quantity;
}

