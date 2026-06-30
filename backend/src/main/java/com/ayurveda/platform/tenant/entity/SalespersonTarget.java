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

    // Three-tier target system
    @Column(name = "target_tier1", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal targetTier1 = BigDecimal.ZERO; // Basic target

    @Column(name = "target_tier2", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal targetTier2 = BigDecimal.ZERO; // Mid target

    @Column(name = "target_tier3", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal targetTier3 = BigDecimal.ZERO; // Stretch target

    @Column(name = "achieved_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal achievedAmount = BigDecimal.ZERO;

    @Column(name = "tier_achieved")
    private Integer tierAchieved; // 0=none, 1=tier1, 2=tier2, 3=tier3
}
