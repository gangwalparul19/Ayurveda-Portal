package com.ayurveda.platform.tenant.repository;

import com.ayurveda.platform.tenant.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for managing order status history and audit trail.
 */
@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    /**
     * Get complete status history for an order, most recent first.
     */
    List<OrderStatusHistory> findAllByOrderIdOrderByChangedAtDesc(Long orderId);

    /**
     * Get status history for an order in chronological order.
     */
    List<OrderStatusHistory> findAllByOrderIdOrderByChangedAtAsc(Long orderId);

    /**
     * Get status changes made by a specific user.
     */
    List<OrderStatusHistory> findAllByChangedByOrderByChangedAtDesc(Long userId);

    /**
     * Get all status changes within a date range.
     */
    List<OrderStatusHistory> findAllByChangedAtBetween(
            LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find orders that transitioned to a specific status.
     */
    List<OrderStatusHistory> findAllByToStatusOrderByChangedAtDesc(String toStatus);

    /**
     * Find orders that transitioned from a specific status.
     */
    List<OrderStatusHistory> findAllByFromStatusOrderByChangedAtDesc(String fromStatus);

    /**
     * Get count of status changes for an order.
     */
    long countByOrderId(Long orderId);

    /**
     * Get recent status changes across all orders.
     */
    @Query("SELECT h FROM OrderStatusHistory h ORDER BY h.changedAt DESC")
    List<OrderStatusHistory> findRecentChanges(org.springframework.data.domain.Pageable pageable);
}
