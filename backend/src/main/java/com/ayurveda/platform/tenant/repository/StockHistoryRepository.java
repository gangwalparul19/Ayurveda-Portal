package com.ayurveda.platform.tenant.repository;

import com.ayurveda.platform.tenant.entity.StockHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for StockHistory entity operations.
 */
@Repository
public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {

    /**
     * Find all stock history for a specific product.
     */
    List<StockHistory> findByProductId(Long productId);

    /**
     * Find stock history for a product within a date range.
     */
    @Query("SELECT sh FROM StockHistory sh WHERE sh.product.id = :productId " +
           "AND sh.createdAt BETWEEN :fromDate AND :toDate ORDER BY sh.createdAt DESC")
    List<StockHistory> findByProductIdAndDateRange(
            @Param("productId") Long productId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );

    /**
     * Find stock history by reference type and ID.
     */
    List<StockHistory> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    /**
     * Find stock history by operation type.
     */
    List<StockHistory> findByOperation(StockHistory.StockOperation operation);

    /**
     * Find stock history performed by a specific user.
     */
    List<StockHistory> findByPerformedBy(Long userId);
}
