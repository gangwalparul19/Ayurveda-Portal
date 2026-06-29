package com.ayurveda.platform.tenant.repository;

import com.ayurveda.platform.tenant.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findAllByOrderId(Long orderId);

    // Product-wise reporting: quantity sold per product in a date range
    @Query("SELECT oi.productNameSnapshot, SUM(oi.quantity), SUM(oi.lineTotal) " +
           "FROM OrderItem oi JOIN oi.order o " +
           "WHERE o.orderDate BETWEEN :start AND :end " +
           "AND o.status NOT IN ('CANCELLED', 'RETURNED') " +
           "GROUP BY oi.productNameSnapshot ORDER BY SUM(oi.lineTotal) DESC")
    List<Object[]> getProductWiseSales(
            @Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Check if a product exists in any order.
     * Used to prevent deletion of products that are referenced in orders.
     * Requirement 8.5: Prevent deletion if product appears in any orders.
     */
    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi WHERE oi.product.id = :productId")
    boolean existsByProductId(@Param("productId") Long productId);
}
