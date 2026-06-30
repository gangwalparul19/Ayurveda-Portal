package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.tenant.entity.Salesperson;
import com.ayurveda.platform.tenant.repository.SalespersonPerformanceRepository;
import com.ayurveda.platform.tenant.repository.SalespersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for salesperson leaderboards and rankings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardService {

    private final SalespersonRepository salespersonRepository;
    private final SalespersonPerformanceRepository performanceRepository;

    /**
     * Get weekly leaderboard (current week).
     */
    public List<Map<String, Object>> getWeeklyLeaderboard() {
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        
        return getLeaderboardForPeriod(startOfWeek, endOfWeek, "week");
    }

    /**
     * Get monthly leaderboard (current month).
     */
    public List<Map<String, Object>> getMonthlyLeaderboard() {
        YearMonth yearMonth = YearMonth.now();
        LocalDate startOfMonth = yearMonth.atDay(1);
        LocalDate endOfMonth = yearMonth.atEndOfMonth();
        
        return getLeaderboardForPeriod(startOfMonth, endOfMonth, "month");
    }

    /**
     * Get quarterly leaderboard (current quarter).
     */
    public List<Map<String, Object>> getQuarterlyLeaderboard() {
        LocalDate today = LocalDate.now();
        int currentMonth = today.getMonthValue();
        int quarterStartMonth = ((currentMonth - 1) / 3) * 3 + 1;
        
        LocalDate startOfQuarter = LocalDate.of(today.getYear(), quarterStartMonth, 1);
        LocalDate endOfQuarter = startOfQuarter.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
        
        return getLeaderboardForPeriod(startOfQuarter, endOfQuarter, "quarter");
    }

    /**
     * Get custom period leaderboard.
     */
    public List<Map<String, Object>> getCustomLeaderboard(LocalDate startDate, LocalDate endDate) {
        return getLeaderboardForPeriod(startDate, endDate, "custom");
    }

    /**
     * Get leaderboard for a specific period.
     */
    private List<Map<String, Object>> getLeaderboardForPeriod(
            LocalDate startDate, LocalDate endDate, String periodType) {
        
        log.info("Generating {} leaderboard from {} to {}", periodType, startDate, endDate);

        // Get all active salespersons
        List<Salesperson> salespersons = salespersonRepository.findByStatus(
                Salesperson.SalespersonStatus.ACTIVE);

        // Calculate total sales for each salesperson in the period
        List<Map<String, Object>> leaderboard = new ArrayList<>();
        
        for (Salesperson sp : salespersons) {
            BigDecimal totalSales = performanceRepository.sumSalesBySalespersonAndDateRange(
                    sp.getId(), startDate, endDate);
            Integer ordersCount = performanceRepository.sumOrdersCountBySalespersonAndDateRange(
                    sp.getId(), startDate, endDate);
            Integer itemsSold = performanceRepository.sumItemsSoldBySalespersonAndDateRange(
                    sp.getId(), startDate, endDate);

            Map<String, Object> entry = new HashMap<>();
            entry.put("salespersonId", sp.getId());
            entry.put("name", sp.getName());
            entry.put("employeeCode", sp.getEmployeeCode());
            entry.put("totalSales", totalSales != null ? totalSales : BigDecimal.ZERO);
            entry.put("ordersCount", ordersCount != null ? ordersCount : 0);
            entry.put("itemsSold", itemsSold != null ? itemsSold : 0);
            
            leaderboard.add(entry);
        }

        // Sort by total sales descending
        leaderboard.sort((a, b) -> {
            BigDecimal salesA = (BigDecimal) a.get("totalSales");
            BigDecimal salesB = (BigDecimal) b.get("totalSales");
            return salesB.compareTo(salesA);
        });

        // Add rank and medals
        for (int i = 0; i < leaderboard.size(); i++) {
            Map<String, Object> entry = leaderboard.get(i);
            entry.put("rank", i + 1);
            
            // Add medals for top 3
            if (i == 0) {
                entry.put("medal", "🥇");
                entry.put("award", "Gold");
            } else if (i == 1) {
                entry.put("medal", "🥈");
                entry.put("award", "Silver");
            } else if (i == 2) {
                entry.put("medal", "🥉");
                entry.put("award", "Bronze");
            }
        }

        // Add period info
        Map<String, Object> result = new HashMap<>();
        result.put("periodType", periodType);
        result.put("startDate", startDate);
        result.put("endDate", endDate);
        result.put("rankings", leaderboard);

        return Collections.singletonList(result);
    }

    /**
     * Get a salesperson's rank in the current period.
     */
    public Map<String, Object> getSalespersonRank(Long salespersonId, String period) {
        List<Map<String, Object>> leaderboard;
        
        switch (period.toLowerCase()) {
            case "week":
                leaderboard = getWeeklyLeaderboard();
                break;
            case "month":
                leaderboard = getMonthlyLeaderboard();
                break;
            case "quarter":
                leaderboard = getQuarterlyLeaderboard();
                break;
            default:
                throw new IllegalArgumentException("Invalid period: " + period);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rankings = (List<Map<String, Object>>) leaderboard.get(0).get("rankings");
        
        Optional<Map<String, Object>> myRank = rankings.stream()
                .filter(r -> r.get("salespersonId").equals(salespersonId))
                .findFirst();

        Map<String, Object> result = new HashMap<>();
        if (myRank.isPresent()) {
            result.put("found", true);
            result.put("rank", myRank.get().get("rank"));
            result.put("totalSales", myRank.get().get("totalSales"));
            result.put("ordersCount", myRank.get().get("ordersCount"));
            result.put("medal", myRank.get().get("medal"));
            result.put("award", myRank.get().get("award"));
            result.put("totalParticipants", rankings.size());
        } else {
            result.put("found", false);
            result.put("message", "No sales data for this period");
        }
        
        result.put("period", period);
        return result;
    }
}
