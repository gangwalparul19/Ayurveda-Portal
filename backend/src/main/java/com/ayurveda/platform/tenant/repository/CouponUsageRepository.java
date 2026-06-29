package com.ayurveda.platform.tenant.repository;

import com.ayurveda.platform.tenant.entity.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for CouponUsage entities.
 */
@Repository
public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    long countByCouponIdAndCustomerPhone(Long couponId, String customerPhone);
}
