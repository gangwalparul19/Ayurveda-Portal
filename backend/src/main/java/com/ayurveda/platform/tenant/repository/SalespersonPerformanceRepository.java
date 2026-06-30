package com.ayurveda.platform.tenant.repository;

import com.ayurveda.platform.tenant.entity.SalespersonPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SalespersonPerformanceRepository extends JpaRepository<SalespersonPerformance, Long> {

    Optional<SalespersonPerformance> findBySalespersonIdAndPerformanceDate(
            Long salespersonId, LocalDate performanceDate);

    List<SalespersonPerformance> findBySalespersonIdAndPerformanceDateBetween(
            Long salespersonId, LocalDate startDate, LocalDate endDate);

    List<SalespersonPerformance> findByPerformanceDateBetweenOrderByTotalSalesDesc(
            LocalDate startDate, LocalDate endDate);

    @Query("SELECT sp FROM SalespersonPerformance sp WHERE sp.performanceDate BETWEEN :startDate AND :endDate " +
           "ORDER BY sp.totalSales DESC")
    List<SalespersonPerformance> findLeaderboard(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(sp.totalSales) FROM SalespersonPerformance sp " +
           "WHERE sp.salespersonId = :salespersonId AND sp.performanceDate BETWEEN :startDate AND :endDate")
    BigDecimal sumSalesBySalespersonAndDateRange(
            @Param("salespersonId") Long salespersonId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(sp.ordersCount) FROM SalespersonPerformance sp " +
           "WHERE sp.salespersonId = :salespersonId AND sp.performanceDate BETWEEN :startDate AND :endDate")
    Integer sumOrdersCountBySalespersonAndDateRange(
            @Param("salespersonId") Long salespersonId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(sp.totalItemsSold) FROM SalespersonPerformance sp " +
           "WHERE sp.salespersonId = :salespersonId AND sp.performanceDate BETWEEN :startDate AND :endDate")
    Integer sumItemsSoldBySalespersonAndDateRange(
            @Param("salespersonId") Long salespersonId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
