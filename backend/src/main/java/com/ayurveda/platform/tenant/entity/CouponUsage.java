package com.ayurveda.platform.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Tracks individual coupon usages per customer to enforce per-user usage limits.
 */
@Entity
@Table(name = "coupon_usages", indexes = {
        @Index(name = "idx_coupon_usages_coupon", columnList = "coupon_id"),
        @Index(name = "idx_coupon_usages_phone", columnList = "customer_phone")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @CreationTimestamp
    @Column(name = "used_at", updatable = false)
    private LocalDateTime usedAt;
}
