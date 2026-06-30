package com.ayurveda.platform.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tracks daily sales performance for leaderboard and analytics.
 * Aggregated from orders on a daily basis.
 */
@Entity
@Table(name = "salesperson_performance", indexes = {
        @Index(name = "idx_performance_salesperson_date", columnList = "salesperson_id,performance_date"),
        @Index(name = "idx_performance_date", columnList = "performance_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalespersonPerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salesperson_id", nullable = false)
    private Long salespersonId;

    @Column(name = "performance_date", nullable = false)
    private LocalDate performanceDate;

    @Column(name = "orders_count", nullable = false)
    @Builder.Default
    private Integer ordersCount = 0;

    @Column(name = "total_sales", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalSales = BigDecimal.ZERO;

    @Column(name = "total_items_sold", nullable = false)
    @Builder.Default
    private Integer totalItemsSold = 0;

    @Column(name = "commission_earned", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal commissionEarned = BigDecimal.ZERO;
}
