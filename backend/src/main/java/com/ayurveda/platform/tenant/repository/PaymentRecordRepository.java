package com.ayurveda.platform.tenant.repository;

import com.ayurveda.platform.tenant.entity.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repository for PaymentRecord entity operations.
 */
@Repository
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    /**
     * Find all payment records for a specific order.
     */
    List<PaymentRecord> findByOrderId(Long orderId);

    /**
     * Calculate total paid amount for an order.
     */
    @Query("SELECT COALESCE(SUM(pr.amount), 0) FROM PaymentRecord pr WHERE pr.order.id = :orderId")
    BigDecimal calculateTotalPaidForOrder(@Param("orderId") Long orderId);

    /**
     * Find payment records by recorded user.
     */
    List<PaymentRecord> findByRecordedBy(Long userId);
}
