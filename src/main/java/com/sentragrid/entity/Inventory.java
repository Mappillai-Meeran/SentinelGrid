package com.sentragrid.entity;

import com.sentragrid.common.AppConstants;
import com.sentragrid.common.BaseEntity;
import com.sentragrid.entity.enums.InventoryState;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Inventory entity with Optimistic Locking support using JPA @Version.
 * Prevents double-booking and concurrent race conditions on stock updates.
 */
@Entity
@Table(name = "inventory", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"pharmacy_id", "medicine_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "medicine_id", nullable = false)
    private Medicine medicine;

    @Min(0)
    @Column(nullable = false)
    private Integer quantity;

    @Min(0)
    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity = 0;

    /**
     * Optimistic locking version field.
     * Automatically incremented by JPA on entity update.
     */
    @Version
    private Long version;

    public Integer getAvailableQuantity() {
        return Math.max(0, quantity - reservedQuantity);
    }

    public InventoryState getInventoryState() {
        int available = getAvailableQuantity();
        if (available == 0) {
            return InventoryState.OUT_OF_STOCK;
        } else if (available <= AppConstants.LOW_STOCK_THRESHOLD) {
            return InventoryState.LOW_STOCK;
        } else {
            return InventoryState.IN_STOCK;
        }
    }
}
