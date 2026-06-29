package com.ayurveda.platform.tenant.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Order line item entity. Stores a snapshot of the product at time of order
 * to prevent data inconsistency if the product is later modified.
 */
@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_items_order", columnList = "order_id"),
        @Index(name = "idx_items_product", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonBackReference
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;

    @Column(name = "sku_snapshot", length = 50)
    private String skuSnapshot;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "mrp_snapshot", precision = 10, scale = 2)
    private BigDecimal mrpSnapshot;

    @Column(name = "discount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    /**
     * Calculate the line total: (quantity × unitPrice) - discount + tax
     * Maintains 2 decimal place precision as per Requirements 4.3, 4.4
     */
    public void calculateLineTotal() {
        // Calculate gross amount: quantity × unitPrice
        BigDecimal gross = unitPrice.multiply(BigDecimal.valueOf(quantity))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        
        // Calculate line total: gross - discount + tax with 2 decimal places
        this.lineTotal = gross
                .subtract(discount != null ? discount : BigDecimal.ZERO)
                .add(taxAmount != null ? taxAmount : BigDecimal.ZERO)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
