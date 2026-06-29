package com.ayurveda.platform.tenant.repository;

import com.ayurveda.platform.tenant.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findAllByStatus(Order.OrderStatus status, Pageable pageable);

    Page<Order> findAllByOrderDateBetween(LocalDate start, LocalDate end, Pageable pageable);

    Page<Order> findAllBySalespersonId(Long salespersonId, Pageable pageable);

    Page<Order> findAllByCustomerId(Long customerId, Pageable pageable);

    List<Order> findAllByStatusAndOrderDateBetween(
            Order.OrderStatus status, LocalDate start, LocalDate end);

    // Reporting queries
    @Query("SELECT COUNT(o) FROM Order o WHERE o.orderDate = :date")
    long countByDate(@Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.orderDate = :date " +
           "AND o.status NOT IN ('CANCELLED', 'RETURNED')")
    BigDecimal sumTotalByDate(@Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE " +
           "o.orderDate BETWEEN :start AND :end AND o.status NOT IN ('CANCELLED', 'RETURNED')")
    BigDecimal sumTotalByDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE " +
           "o.salespersonId = :salespersonId AND o.orderDate BETWEEN :start AND :end " +
           "AND o.status NOT IN ('CANCELLED', 'RETURNED')")
    BigDecimal sumTotalBySalespersonAndDateRange(
            @Param("salespersonId") Long salespersonId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.salespersonId = :salespersonId " +
           "AND o.orderDate BETWEEN :start AND :end")
    long countBySalespersonAndDateRange(
            @Param("salespersonId") Long salespersonId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    // For order number generation: find the last order number for today
    @Query("SELECT o.orderNumber FROM Order o WHERE o.orderDate = :date ORDER BY o.id DESC LIMIT 1")
    Optional<String> findLastOrderNumberByDate(@Param("date") LocalDate date);

    // Report service methods
    // Eagerly fetch items + customer in a single query to avoid N+1 lazy loads
    // when report/billing aggregations iterate order.getItems() / order.getCustomer().
    @EntityGraph(attributePaths = {"items", "customer"})
    List<Order> findByOrderDate(LocalDate date);

    @EntityGraph(attributePaths = {"items", "customer"})
    List<Order> findByOrderDateBetween(LocalDate start, LocalDate end);

    long countByStatusIn(List<Order.OrderStatus> statuses);

    // Dispatch queue: orders ready for dispatch
    Page<Order> findAllByStatusIn(List<Order.OrderStatus> statuses, Pageable pageable);

    /**
     * Find orders by salesperson and date range for target achievement tracking.
     */
    List<Order> findBySalespersonIdAndOrderDateBetween(Long salespersonId, LocalDate start, LocalDate end);

    /**
     * Find orders by customer and date range for duplicate detection.
     * Eagerly fetch items to avoid N+1 loads when computing product-set similarity.
     */
    @EntityGraph(attributePaths = {"items"})
    List<Order> findByCustomerIdAndOrderDateBetween(Long customerId, LocalDate start, LocalDate end);

    /**
     * Find all orders for a customer, newest first — used by the storefront "My Orders" endpoint.
     */
    Page<Order> findByCustomerIdOrderByOrderDateDesc(Long customerId, Pageable pageable);

    /**
     * Find a single order by ID and customer ID — used to prevent storefront users from
     * accessing another customer's order detail.
     */
    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);
}
