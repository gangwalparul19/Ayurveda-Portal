package com.ayurveda.platform.controller;

import com.ayurveda.platform.dto.request.CreateSalespersonRequest;
import com.ayurveda.platform.dto.request.UpdateSalespersonRequest;
import com.ayurveda.platform.dto.response.SalespersonResponse;
import com.ayurveda.platform.tenant.service.ReportService;
import com.ayurveda.platform.tenant.service.SalespersonManagementService;
import com.ayurveda.platform.tenant.service.SalespersonTargetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for salesperson CRUD operations, target management and performance tracking.
 */
@RestController
@RequestMapping("/salesperson")
@RequiredArgsConstructor
@Slf4j
public class SalespersonController {

    private final SalespersonManagementService salespersonManagementService;
    private final SalespersonTargetService targetService;
    private final ReportService reportService;

    // ==================== CRUD Operations ====================

    /**
     * Create a new salesperson.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<SalespersonResponse> createSalesperson(
            @Valid @RequestBody CreateSalespersonRequest request) {
        log.info("Creating salesperson with employee code: {}", request.getEmployeeCode());
        SalespersonResponse response = salespersonManagementService.createSalesperson(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update an existing salesperson.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<SalespersonResponse> updateSalesperson(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSalespersonRequest request) {
        log.info("Updating salesperson with ID: {}", id);
        SalespersonResponse response = salespersonManagementService.updateSalesperson(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a salesperson.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> deleteSalesperson(@PathVariable Long id) {
        log.info("Deleting salesperson with ID: {}", id);
        salespersonManagementService.deleteSalesperson(id);
        return ResponseEntity.ok(Map.of("message", "Salesperson deleted successfully"));
    }

    /**
     * Get a salesperson by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<SalespersonResponse> getSalespersonById(@PathVariable Long id) {
        log.info("Fetching salesperson with ID: {}", id);
        SalespersonResponse response = salespersonManagementService.getSalespersonById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Get a salesperson by employee code.
     */
    @GetMapping("/by-employee-code/{employeeCode}")
    public ResponseEntity<SalespersonResponse> getSalespersonByEmployeeCode(
            @PathVariable String employeeCode) {
        log.info("Fetching salesperson with employee code: {}", employeeCode);
        SalespersonResponse response = salespersonManagementService
                .getSalespersonByEmployeeCode(employeeCode);
        return ResponseEntity.ok(response);
    }

    /**
     * Get a salesperson by platform user ID.
     */
    @GetMapping("/by-platform-user/{platformUserId}")
    public ResponseEntity<SalespersonResponse> getSalespersonByPlatformUserId(
            @PathVariable Long platformUserId) {
        log.info("Fetching salesperson with platform user ID: {}", platformUserId);
        SalespersonResponse response = salespersonManagementService
                .getSalespersonByPlatformUserId(platformUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all salespersons with pagination.
     */
    @GetMapping
    public ResponseEntity<Page<SalespersonResponse>> getAllSalespersons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {
        log.info("Fetching all salespersons - page: {}, size: {}", page, size);
        
        Sort sort = sortDirection.equalsIgnoreCase("DESC") 
                ? Sort.by(sortBy).descending() 
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<SalespersonResponse> response = salespersonManagementService
                .getAllSalespersons(pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Get salespersons by status.
     */
    @GetMapping("/by-status/{status}")
    public ResponseEntity<List<SalespersonResponse>> getSalespersonsByStatus(
            @PathVariable String status) {
        log.info("Fetching salespersons with status: {}", status);
        List<SalespersonResponse> response = salespersonManagementService
                .getSalespersonsByStatus(status);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all active salespersons.
     */
    @GetMapping("/active")
    public ResponseEntity<List<SalespersonResponse>> getActiveSalespersons() {
        log.info("Fetching all active salespersons");
        List<SalespersonResponse> response = salespersonManagementService.getActiveSalespersons();
        return ResponseEntity.ok(response);
    }

    /**
     * Check if employee code exists.
     */
    @GetMapping("/check-employee-code/{employeeCode}")
    public ResponseEntity<Map<String, Boolean>> checkEmployeeCodeExists(
            @PathVariable String employeeCode) {
        boolean exists = salespersonManagementService.employeeCodeExists(employeeCode);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    /**
     * Update salesperson status.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<SalespersonResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        log.info("Updating status for salesperson ID: {} to {}", id, request.get("status"));
        String status = request.get("status");
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        UpdateSalespersonRequest updateRequest = UpdateSalespersonRequest.builder()
                .status(status)
                .build();
        
        SalespersonResponse response = salespersonManagementService.updateSalesperson(id, updateRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Get sales report for a salesperson.
     */
    @GetMapping("/{id}/sales")
    public ResponseEntity<Map<String, Object>> getSalespersonSalesReport(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Fetching sales report for salesperson ID: {} from {} to {}", id, startDate, endDate);
        Map<String, Object> report = reportService.getSalespersonSalesReport(id, startDate, endDate);
        return ResponseEntity.ok(report);
    }

    // ==================== Target Management Operations ====================

    /**
     * Set a monthly target for a salesperson.
     */
    @PostMapping("/targets")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> setTarget(
            @RequestBody Map<String, Object> request) {
        Long salespersonId = Long.valueOf(request.get("salespersonId").toString());
        Integer month = Integer.valueOf(request.get("month").toString());
        Integer year = Integer.valueOf(request.get("year").toString());
        BigDecimal targetAmount = new BigDecimal(request.get("targetAmount").toString());

        var target = targetService.setTarget(salespersonId, month, year, targetAmount);

        return ResponseEntity.ok(Map.of(
                "id", target.getId(),
                "salespersonId", target.getSalespersonUserId(),
                "month", target.getMonth(),
                "year", target.getYear(),
                "targetAmount", target.getTargetAmount(),
                "achievedAmount", target.getAchievedAmount()
        ));
    }

    /**
     * Get target for a specific salesperson and month.
     */
    @GetMapping("/targets")
    public ResponseEntity<Map<String, Object>> getTarget(
            @RequestParam Long salespersonId,
            @RequestParam Integer month,
            @RequestParam Integer year) {
        var target = targetService.getTarget(salespersonId, month, year);

        return ResponseEntity.ok(Map.of(
                "id", target.getId(),
                "salespersonId", target.getSalespersonUserId(),
                "month", target.getMonth(),
                "year", target.getYear(),
                "targetAmount", target.getTargetAmount(),
                "achievedAmount", target.getAchievedAmount()
        ));
    }

    /**
     * Get all targets for a salesperson.
     */
    @GetMapping("/{salespersonId}/targets")
    public ResponseEntity<List<Map<String, Object>>> getAllTargets(
            @PathVariable Long salespersonId) {
        var targets = targetService.getAllTargetsForSalesperson(salespersonId);

        List<Map<String, Object>> response = targets.stream()
                .map(target -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", target.getId());
                    map.put("month", target.getMonth());
                    map.put("year", target.getYear());
                    map.put("targetAmount", target.getTargetAmount());
                    map.put("achievedAmount", target.getAchievedAmount());
                    return map;
                })
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Get achievement summary for a salesperson.
     */
    @GetMapping("/{salespersonId}/achievement")
    public ResponseEntity<Map<String, Object>> getAchievementSummary(
            @PathVariable Long salespersonId,
            @RequestParam Integer month,
            @RequestParam Integer year) {
        Map<String, Object> summary = targetService.getAchievementSummary(
                salespersonId, month, year);
        return ResponseEntity.ok(summary);
    }

    /**
     * Get yearly achievements for a salesperson.
     */
    @GetMapping("/{salespersonId}/yearly-achievements")
    public ResponseEntity<List<Map<String, Object>>> getYearlyAchievements(
            @PathVariable Long salespersonId,
            @RequestParam Integer year) {
        var targets = targetService.getYearlyAchievements(salespersonId, year);

        List<Map<String, Object>> response = targets.stream()
                .map(target -> {
                    BigDecimal percentage = BigDecimal.ZERO;
                    if (target.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {
                        percentage = target.getAchievedAmount()
                                .multiply(BigDecimal.valueOf(100))
                                .divide(target.getTargetAmount(), 2, RoundingMode.HALF_UP);
                    }

                    Map<String, Object> map = new HashMap<>();
                    map.put("month", target.getMonth());
                    map.put("targetAmount", target.getTargetAmount());
                    map.put("achievedAmount", target.getAchievedAmount());
                    map.put("percentageAchieved", percentage);
                    return map;
                })
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Recalculate achievements for a specific month/year.
     */
    @PostMapping("/targets/recalculate")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> recalculateAchievements(
            @RequestParam Integer month,
            @RequestParam Integer year) {
        targetService.recalculateAchievements(month, year);
        return ResponseEntity.ok(Map.of(
                "message", "Achievements recalculated successfully",
                "month", month.toString(),
                "year", year.toString()
        ));
    }

    /**
     * Get all targets for a specific month/year.
     */
    @GetMapping("/targets/month")
    public ResponseEntity<List<Map<String, Object>>> getTargetsForMonth(
            @RequestParam Integer month,
            @RequestParam Integer year) {
        var targets = targetService.getTargetsForMonth(month, year);

        List<Map<String, Object>> response = targets.stream()
                .map(target -> {
                    BigDecimal percentage = BigDecimal.ZERO;
                    if (target.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {
                        percentage = target.getAchievedAmount()
                                .multiply(BigDecimal.valueOf(100))
                                .divide(target.getTargetAmount(), 2, RoundingMode.HALF_UP);
                    }

                    Map<String, Object> map = new HashMap<>();
                    map.put("id", target.getId());
                    map.put("salespersonId", target.getSalespersonUserId());
                    map.put("targetAmount", target.getTargetAmount());
                    map.put("achievedAmount", target.getAchievedAmount());
                    map.put("percentageAchieved", percentage);
                    return map;
                })
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Delete a target.
     */
    @DeleteMapping("/targets/{targetId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> deleteTarget(@PathVariable Long targetId) {
        targetService.deleteTarget(targetId);
        return ResponseEntity.ok(Map.of("message", "Target deleted successfully"));
    }
}
