package com.ayurveda.platform.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * StockHistory entity tracks all stock movements for audit purposes.
 * Records quantity before, changed, and after for each operation.
 */
@Entity
@Table(name = "stock_history", indexes = {
        @Index(name = "idx_stock_product", columnList = "product_id"),
        @Index(name = "idx_stock_created", columnList = "created_at"),
        @Index(name = "idx_stock_operation", columnList = "operation")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false)
    private StockOperation operation;

    @Column(name = "quantity_before", nullable = false)
    private Integer quantityBefore;

    @Column(name = "quantity_changed", nullable = false)
    private Integer quantityChanged;

    @Column(name = "quantity_after", nullable = false)
    private Integer quantityAfter;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "performed_by")
    private Long performedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Enum for stock operation types
     */
    public enum StockOperation {
        STOCK_IN, STOCK_OUT, ADJUSTMENT, RETURN
    }
}
