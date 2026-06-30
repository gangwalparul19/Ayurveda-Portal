package com.ayurveda.platform.controller;

import com.ayurveda.platform.dto.response.SalespersonOrderResponse;
import com.ayurveda.platform.tenant.service.LeaderboardService;
import com.ayurveda.platform.tenant.service.SalespersonDashboardService;
import com.ayurveda.platform.tenant.service.SalespersonOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controller for salesperson personal dashboard and performance tracking.
 * Accessible by SALESPERSON role.
 */
@RestController
@RequestMapping("/salesperson-dashboard")
@RequiredArgsConstructor
@Slf4j
public class SalespersonDashboardController {

    private final SalespersonDashboardService dashboardService;
    private final LeaderboardService leaderboardService;
    private final SalespersonOrderService salespersonOrderService;

    /**
     * Get complete dashboard for logged-in salesperson.
     */
    @GetMapping("/{salespersonId}")
    @PreAuthorize("hasRole('SALESPERSON') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<Map<String, Object>> getDashboard(
            @PathVariable Long salespersonId) {
        log.info("Fetching dashboard for salesperson ID: {}", salespersonId);
        Map<String, Object> dashboard = dashboardService.getDashboard(salespersonId);
        return ResponseEntity.ok(dashboard);
    }

    /**
     * Get today's performance.
     */
    @GetMapping("/{salespersonId}/today")
    @PreAuthorize("hasRole('SALESPERSON') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<Map<String, Object>> getTodayPerformance(
            @PathVariable Long salespersonId) {
        log.info("Fetching today's performance for salesperson ID: {}", salespersonId);
        Map<String, Object> performance = dashboardService.getDayPerformance(
                salespersonId, LocalDate.now());
        return ResponseEntity.ok(performance);
    }

    /**
     * Get performance for a specific day.
     */
    @GetMapping("/{salespersonId}/day")
    @PreAuthorize("hasRole('SALESPERSON') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<Map<String, Object>> getDayPerformance(
            @PathVariable Long salespersonId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("Fetching day performance for salesperson ID: {} on {}", salespersonId, date);
        Map<String, Object> performance = dashboardService.getDayPerformance(salespersonId, date);
        return ResponseEntity.ok(performance);
    }

    /**
     * Get this week's performance.
     */
    @GetMapping("/{salespersonId}/week")
    @PreAuthorize("hasRole('SALESPERSON') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<Map<String, Object>> getWeekPerformance(
            @PathVariable Long salespersonId) {
        log.info("Fetching week performance for salesperson ID: {}", salespersonId);
        Map<String, Object> performance = dashboardService.getWeekPerformance(
                salespersonId, LocalDate.now());
        return ResponseEntity.ok(performance);
    }

    /**
     * Get this month's performance.
     */
    @GetMapping("/{salespersonId}/month")
    @PreAuthorize("hasRole('SALESPERSON') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<Map<String, Object>> getMonthPerformance(
            @PathVariable Long salespersonId) {
        log.info("Fetching month performance for salesperson ID: {}", salespersonId);
        Map<String, Object> performance = dashboardService.getMonthPerformance(
                salespersonId, LocalDate.now());
        return ResponseEntity.ok(performance);
    }

    /**
     * Get target achievement status.
     */
    @GetMapping("/{salespersonId}/target-achievement")
    @PreAuthorize("hasRole('SALESPERSON') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<Map<String, Object>> getTargetAchievement(
            @PathVariable Long salespersonId) {
        log.info("Fetching target achievement for salesperson ID: {}", salespersonId);
        Map<String, Object> achievement = dashboardService.getTargetAchievement(
                salespersonId, LocalDate.now());
        return ResponseEntity.ok(achievement);
    }

    /**
     * Get recent performance trend (last 7 days).
     */
    @GetMapping("/{salespersonId}/trend")
    @PreAuthorize("hasRole('SALESPERSON') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getRecentTrend(
            @PathVariable Long salespersonId) {
        log.info("Fetching performance trend for salesperson ID: {}", salespersonId);
        List<Map<String, Object>> trend = dashboardService.getRecentTrend(
                salespersonId, LocalDate.now());
        return ResponseEntity.ok(trend);
    }

    // ==================== Leaderboard Endpoints ====================

    /**
     * Get weekly leaderboard.
     */
    @GetMapping("/leaderboard/week")
    @PreAuthorize("hasRole('SALESPERSON') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getWeeklyLeaderboard() {
        log.info("Fetching weekly leaderboard");
        List<Map<String, Object>> leaderboard = leaderboardService.getWeeklyLeaderboard();
        return ResponseEntity.ok(leaderboard);
    }

    /**
     * Get monthly leaderboard.
     */
    @GetMapping("/leaderboard/month")
    @PreAuthorize("hasRole('SALESPERSON') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getMonthlyLeaderboard() {
        log.info("Fetching monthly leaderboard");
        List<Map<String, Object>> leaderboard = leaderboardService.getMonthlyLeaderboard();
        return ResponseEntity.ok(leaderboard);
    }

    /**
     * Get quarterly leaderboard.
     */
    @GetMapping("/leaderboard/quarter")
    @PreAuthorize("hasRole('SALESPERSON') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getQuarterlyLeaderboard() {
        log.info("Fetching quarterly leaderboard");
        List<Map<String, Object>> leaderboard = leaderboardService.getQuarterlyLeaderboard();
        return ResponseEntity.ok(leaderboard);
    }

    /**
     * Get custom period leaderboard.
     */
    @GetMapping("/leaderboard/custom")
    @PreAuthorize("hasRole('SALESPERSON') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getCustomLeaderboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Fetching custom leaderboard from {} to {}", startDate, endDate);
        List<Map<String, Object>> leaderboard = leaderboardService.getCustomLeaderboard(
                startDate, endDate);
        return ResponseEntity.ok(leaderboard);
    }

    /**
     * Get my rank in a specific period.
     */
    @GetMapping("/{salespersonId}/my-rank/{period}")
    @PreAuthorize("hasRole('SALESPERSON') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<Map<String, Object>> getMyRank(
            @PathVariable Long salespersonId,
            @PathVariable String period) {
        log.info("Fetching rank for salesperson ID: {} in period: {}", salespersonId, period);
        Map<String, Object> rank = leaderboardService.getSalespersonRank(salespersonId, period);
        return ResponseEntity.ok(rank);
    }

    // ==================== Salesperson Order View (Masked) ====================

    /**
     * Get my orders (salesperson's own orders with masked customer details).
     * SALESPERSON role: sees only their own orders with masked phone/address.
     * MANAGER/ADMIN role: sees full details.
     */
    @GetMapping("/{salespersonId}/my-orders")
    @PreAuthorize("hasRole('SALESPERSON') or hasRole('MANAGER') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<Page<SalespersonOrderResponse>> getMyOrders(
            @PathVariable Long salespersonId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        log.info("Fetching orders for salesperson ID: {} by user: {}", salespersonId, principal.getName());
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("orderDate").descending());
        Page<SalespersonOrderResponse> orders = salespersonOrderService.getMyOrders(
                principal.getName(), pageable);
        
        return ResponseEntity.ok(orders);
    }

    /**
     * Get a specific order detail.
     * SALESPERSON: can only view their own orders with masked details.
     * MANAGER/ADMIN: can view any order with full details.
     */
    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasRole('SALESPERSON') or hasRole('MANAGER') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<SalespersonOrderResponse> getOrderDetail(
            @PathVariable Long orderId,
            Principal principal) {
        log.info("Fetching order detail for order ID: {} by user: {}", orderId, principal.getName());
        
        SalespersonOrderResponse order = salespersonOrderService.getOrderById(
                principal.getName(), orderId);
        
        return ResponseEntity.ok(order);
    }

    /**
     * Get all salesperson orders (Sales Head / Admin only).
     * Shows full customer details without masking.
     */
    @GetMapping("/all-orders")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<Page<SalespersonOrderResponse>> getAllSalespersonOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        log.info("Fetching all salesperson orders by user: {}", principal.getName());
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("orderDate").descending());
        Page<SalespersonOrderResponse> orders = salespersonOrderService.getAllSalespersonOrders(
                principal.getName(), pageable);
        
        return ResponseEntity.ok(orders);
    }
}
