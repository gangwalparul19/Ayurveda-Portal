package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.tenant.entity.Salesperson;
import com.ayurveda.platform.tenant.entity.SalespersonPerformance;
import com.ayurveda.platform.tenant.entity.SalespersonTarget;
import com.ayurveda.platform.tenant.repository.SalespersonPerformanceRepository;
import com.ayurveda.platform.tenant.repository.SalespersonRepository;
import com.ayurveda.platform.tenant.repository.SalespersonTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Service for salesperson dashboard and performance tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SalespersonDashboardService {

    private final SalespersonRepository salespersonRepository;
    private final SalespersonPerformanceRepository performanceRepository;
    private final SalespersonTargetRepository targetRepository;

    /**
     * Get comprehensive dashboard data for a salesperson.
     */
    public Map<String, Object> getDashboard(Long salespersonId) {
        log.info("Fetching dashboard for salesperson ID: {}", salespersonId);

        Salesperson salesperson = salespersonRepository.findById(salespersonId)
                .orElseThrow(() -> new RuntimeException("Salesperson not found"));

        LocalDate today = LocalDate.now();
        Map<String, Object> dashboard = new HashMap<>();

        // Basic info
        dashboard.put("salespersonInfo", getSalespersonInfo(salesperson));

        // Today's performance
        dashboard.put("todayPerformance", getDayPerformance(salespersonId, today));

        // This week's performance
        dashboard.put("weekPerformance", getWeekPerformance(salespersonId, today));

        // This month's performance
        dashboard.put("monthPerformance", getMonthPerformance(salespersonId, today));

        // Target achievement
        dashboard.put("targetAchievement", getTargetAchievement(salespersonId, today));

        // Recent performance trend (last 7 days)
        dashboard.put("recentTrend", getRecentTrend(salespersonId, today));

        return dashboard;
    }

    /**
     * Get salesperson basic info.
     */
    private Map<String, Object> getSalespersonInfo(Salesperson salesperson) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", salesperson.getId());
        info.put("name", salesperson.getName());
        info.put("employeeCode", salesperson.getEmployeeCode());
        info.put("commissionRate", salesperson.getCommissionRate());
        info.put("status", salesperson.getStatus());
        return info;
    }

    /**
     * Get performance for a specific day.
     */
    public Map<String, Object> getDayPerformance(Long salespersonId, LocalDate date) {
        Optional<SalespersonPerformance> perfOpt = performanceRepository
                .findBySalespersonIdAndPerformanceDate(salespersonId, date);

        Map<String, Object> dayPerf = new HashMap<>();
        if (perfOpt.isPresent()) {
            SalespersonPerformance perf = perfOpt.get();
            dayPerf.put("date", date);
            dayPerf.put("ordersCount", perf.getOrdersCount());
            dayPerf.put("totalSales", perf.getTotalSales());
            dayPerf.put("totalItemsSold", perf.getTotalItemsSold());
            dayPerf.put("commissionEarned", perf.getCommissionEarned());
        } else {
            dayPerf.put("date", date);
            dayPerf.put("ordersCount", 0);
            dayPerf.put("totalSales", BigDecimal.ZERO);
            dayPerf.put("totalItemsSold", 0);
            dayPerf.put("commissionEarned", BigDecimal.ZERO);
        }
        return dayPerf;
    }

    /**
     * Get performance for current week (Monday to Sunday).
     */
    public Map<String, Object> getWeekPerformance(Long salespersonId, LocalDate referenceDate) {
        LocalDate startOfWeek = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = referenceDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        BigDecimal totalSales = performanceRepository.sumSalesBySalespersonAndDateRange(
                salespersonId, startOfWeek, endOfWeek);
        Integer ordersCount = performanceRepository.sumOrdersCountBySalespersonAndDateRange(
                salespersonId, startOfWeek, endOfWeek);
        Integer itemsSold = performanceRepository.sumItemsSoldBySalespersonAndDateRange(
                salespersonId, startOfWeek, endOfWeek);

        Map<String, Object> weekPerf = new HashMap<>();
        weekPerf.put("startDate", startOfWeek);
        weekPerf.put("endDate", endOfWeek);
        weekPerf.put("ordersCount", ordersCount != null ? ordersCount : 0);
        weekPerf.put("totalSales", totalSales != null ? totalSales : BigDecimal.ZERO);
        weekPerf.put("totalItemsSold", itemsSold != null ? itemsSold : 0);

        return weekPerf;
    }

    /**
     * Get performance for current month.
     */
    public Map<String, Object> getMonthPerformance(Long salespersonId, LocalDate referenceDate) {
        YearMonth yearMonth = YearMonth.from(referenceDate);
        LocalDate startOfMonth = yearMonth.atDay(1);
        LocalDate endOfMonth = yearMonth.atEndOfMonth();

        BigDecimal totalSales = performanceRepository.sumSalesBySalespersonAndDateRange(
                salespersonId, startOfMonth, endOfMonth);
        Integer ordersCount = performanceRepository.sumOrdersCountBySalespersonAndDateRange(
                salespersonId, startOfMonth, endOfMonth);
        Integer itemsSold = performanceRepository.sumItemsSoldBySalespersonAndDateRange(
                salespersonId, startOfMonth, endOfMonth);

        Map<String, Object> monthPerf = new HashMap<>();
        monthPerf.put("month", yearMonth.getMonthValue());
        monthPerf.put("year", yearMonth.getYear());
        monthPerf.put("ordersCount", ordersCount != null ? ordersCount : 0);
        monthPerf.put("totalSales", totalSales != null ? totalSales : BigDecimal.ZERO);
        monthPerf.put("totalItemsSold", itemsSold != null ? itemsSold : 0);

        return monthPerf;
    }

    /**
     * Get target achievement status with tier levels.
     */
    public Map<String, Object> getTargetAchievement(Long salespersonId, LocalDate referenceDate) {
        YearMonth yearMonth = YearMonth.from(referenceDate);
        
        Optional<SalespersonTarget> targetOpt = targetRepository
                .findBySalespersonUserIdAndMonthAndYear(
                        salespersonId, yearMonth.getMonthValue(), yearMonth.getYear());

        Map<String, Object> achievement = new HashMap<>();
        
        if (targetOpt.isPresent()) {
            SalespersonTarget target = targetOpt.get();
            
            achievement.put("hasTarget", true);
            achievement.put("targetTier1", target.getTargetTier1());
            achievement.put("targetTier2", target.getTargetTier2());
            achievement.put("targetTier3", target.getTargetTier3());
            achievement.put("achievedAmount", target.getAchievedAmount());
            achievement.put("tierAchieved", target.getTierAchieved());
            
            // Calculate percentages for each tier
            achievement.put("percentageTier1", calculatePercentage(target.getAchievedAmount(), target.getTargetTier1()));
            achievement.put("percentageTier2", calculatePercentage(target.getAchievedAmount(), target.getTargetTier2()));
            achievement.put("percentageTier3", calculatePercentage(target.getAchievedAmount(), target.getTargetTier3()));
            
            // Determine current tier status
            String status = "Below Target";
            if (target.getAchievedAmount().compareTo(target.getTargetTier3()) >= 0) {
                status = "Tier 3 Achieved! 🏆";
            } else if (target.getAchievedAmount().compareTo(target.getTargetTier2()) >= 0) {
                status = "Tier 2 Achieved! 🥈";
            } else if (target.getAchievedAmount().compareTo(target.getTargetTier1()) >= 0) {
                status = "Tier 1 Achieved! 🥉";
            }
            achievement.put("status", status);
        } else {
            achievement.put("hasTarget", false);
            achievement.put("message", "No target set for this month");
        }

        return achievement;
    }

    /**
     * Get recent performance trend (last 7 days).
     */
    public List<Map<String, Object>> getRecentTrend(Long salespersonId, LocalDate referenceDate) {
        LocalDate startDate = referenceDate.minusDays(6);
        List<SalespersonPerformance> performances = performanceRepository
                .findBySalespersonIdAndPerformanceDateBetween(salespersonId, startDate, referenceDate);

        List<Map<String, Object>> trend = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(referenceDate); date = date.plusDays(1)) {
            LocalDate currentDate = date;
            Optional<SalespersonPerformance> perfOpt = performances.stream()
                    .filter(p -> p.getPerformanceDate().equals(currentDate))
                    .findFirst();

            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", currentDate);
            if (perfOpt.isPresent()) {
                SalespersonPerformance perf = perfOpt.get();
                dayData.put("totalSales", perf.getTotalSales());
                dayData.put("ordersCount", perf.getOrdersCount());
            } else {
                dayData.put("totalSales", BigDecimal.ZERO);
                dayData.put("ordersCount", 0);
            }
            trend.add(dayData);
        }

        return trend;
    }

    /**
     * Calculate percentage achievement.
     */
    private BigDecimal calculatePercentage(BigDecimal achieved, BigDecimal target) {
        if (target == null || target.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return achieved.multiply(BigDecimal.valueOf(100))
                .divide(target, 2, RoundingMode.HALF_UP);
    }
}
