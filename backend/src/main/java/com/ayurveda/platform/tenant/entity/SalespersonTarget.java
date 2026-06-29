package com.ayurveda.platform.tenant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Monthly sales targets and achievements per salesperson.
 */
@Entity
@Table(name = "salesperson_targets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalespersonTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salesperson_user_id", nullable = false)
    private Long salespersonUserId;

    @Column(name = "month", nullable = false)
    private Integer month;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "target_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal targetAmount = BigDecimal.ZERO;

    @Column(name = "achieved_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal achievedAmount = BigDecimal.ZERO;
}
