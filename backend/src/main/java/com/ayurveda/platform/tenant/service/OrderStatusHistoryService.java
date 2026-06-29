package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.OrderStatusHistory;
import com.ayurveda.platform.tenant.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing order status history and audit trail.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class OrderStatusHistoryService {

    private final OrderStatusHistoryRepository historyRepository;

    /**
     * Record a status change for an order.
     */
    public OrderStatusHistory recordStatusChange(Order order, String fromStatus, String toStatus, 
                                                  Long changedBy, String notes) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedBy(changedBy)
                .notes(notes)
                .build();

        OrderStatusHistory saved = historyRepository.save(history);
        log.debug("Recorded status change for order {}: {} -> {}", 
                order.getId(), fromStatus, toStatus);
        return saved;
    }

    /**
     * Get complete status history for an order, most recent first.
     */
    public List<OrderStatusHistory> getOrderHistory(Long orderId) {
        return historyRepository.findAllByOrderIdOrderByChangedAtDesc(orderId);
    }

    /**
     * Get status history in chronological order.
     */
    public List<OrderStatusHistory> getOrderHistoryChronological(Long orderId) {
        return historyRepository.findAllByOrderIdOrderByChangedAtAsc(orderId);
    }

    /**
     * Get all status changes made by a specific user.
     */
    public List<OrderStatusHistory> getChangesByUser(Long userId) {
        return historyRepository.findAllByChangedByOrderByChangedAtDesc(userId);
    }

    /**
     * Get status changes within a date range.
     */
    public List<OrderStatusHistory> getChangesInDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return historyRepository.findAllByChangedAtBetween(startDate, endDate);
    }

    /**
     * Get recent status changes across all orders.
     */
    public List<OrderStatusHistory> getRecentChanges(int limit) {
        return historyRepository.findRecentChanges(PageRequest.of(0, limit));
    }

    /**
     * Get orders that transitioned to a specific status.
     */
    public List<OrderStatusHistory> getOrdersByToStatus(String status) {
        return historyRepository.findAllByToStatusOrderByChangedAtDesc(status);
    }

    /**
     * Get orders that transitioned from a specific status.
     */
    public List<OrderStatusHistory> getOrdersByFromStatus(String status) {
        return historyRepository.findAllByFromStatusOrderByChangedAtDesc(status);
    }

    /**
     * Get the count of status changes for an order.
     */
    public long getChangeCount(Long orderId) {
        return historyRepository.countByOrderId(orderId);
    }

    /**
     * Get the most recent status change for an order.
     */
    public OrderStatusHistory getLatestChange(Long orderId) {
        List<OrderStatusHistory> history = historyRepository.findAllByOrderIdOrderByChangedAtDesc(orderId);
        return history.isEmpty() ? null : history.get(0);
    }
}
