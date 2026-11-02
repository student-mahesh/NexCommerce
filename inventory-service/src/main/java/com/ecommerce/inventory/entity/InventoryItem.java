package com.ecommerce.inventory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "inventory_items", indexes = {
        @Index(name = "idx_inventory_product_id", columnList = "product_id", unique = true),
        @Index(name = "idx_inventory_sku", columnList = "sku", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    @Builder.Default
    private Integer reservedQuantity = 0;

    @Version
    private Long version;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public boolean hasSufficientStock(int requestedQuantity) {
        return this.availableQuantity != null && this.availableQuantity >= requestedQuantity;
    }

    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(String.format("Reservation quantity must be positive, got: %d", quantity));
        }
        if (!hasSufficientStock(quantity)) {
            throw new IllegalStateException(String.format(
                    "Cannot reserve %d units for productId %d; available stock is %d",
                    quantity, productId, availableQuantity));
        }
        this.availableQuantity -= quantity;
        this.reservedQuantity = (this.reservedQuantity == null ? 0 : this.reservedQuantity) + quantity;
    }

    public void release(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(String.format("Release quantity must be positive, got: %d", quantity));
        }
        int currentReserved = (this.reservedQuantity == null ? 0 : this.reservedQuantity);
        if (quantity > currentReserved) {
            throw new IllegalStateException(String.format(
                    "Cannot release %d units for productId %d; only %d units are currently reserved",
                    quantity, productId, currentReserved));
        }
        this.availableQuantity = (this.availableQuantity == null ? 0 : this.availableQuantity) + quantity;
        this.reservedQuantity = currentReserved - quantity;
    }

    public void fulfill(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(String.format("Fulfill quantity must be positive, got: %d", quantity));
        }
        int currentReserved = (this.reservedQuantity == null ? 0 : this.reservedQuantity);
        if (quantity > currentReserved) {
            throw new IllegalStateException(String.format(
                    "Cannot fulfill %d units for productId %d; only %d units are currently reserved",
                    quantity, productId, currentReserved));
        }
        this.reservedQuantity = currentReserved - quantity;
    }
}
