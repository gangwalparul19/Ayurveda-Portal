package com.ayurveda.platform.controller;

import com.ayurveda.platform.dto.request.TenantOnboardRequest;
import com.ayurveda.platform.master.entity.Tenant;
import com.ayurveda.platform.master.entity.PlatformUser;
import com.ayurveda.platform.master.service.TenantService;
import com.ayurveda.platform.master.service.PlatformUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Super-Admin controller for platform-level tenant management.
 * All endpoints require SUPER_ADMIN role (enforced by SecurityConfig + @PreAuthorize).
 */
@RestController
@RequestMapping("/admin/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Profile("dev")  // Only load in dev mode (multi-tenant)
public class TenantController {

    private final TenantService tenantService;
    private final PlatformUserService userService;

    /**
     * List all tenants on the platform.
     */
    @GetMapping
    public ResponseEntity<List<Tenant>> getAllTenants() {
        return ResponseEntity.ok(tenantService.getAllTenants());
    }

    /**
     * Get a specific tenant by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Tenant> getTenantById(@PathVariable Long id) {
        return ResponseEntity.ok(tenantService.getTenantById(id));
    }

    /**
     * Onboard a new tenant with initial admin user.
     */
    @PostMapping
    public ResponseEntity<Tenant> onboardTenant(
            @Valid @RequestBody TenantOnboardRequest request) {
        Tenant tenant = tenantService.onboardTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(tenant);
    }

    /**
     * Update tenant status (ACTIVE, SUSPENDED, ONBOARDING).
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Tenant> updateTenantStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> statusUpdate) {
        String statusStr = statusUpdate.get("status");
        Tenant.TenantStatus status = Tenant.TenantStatus.valueOf(statusStr.toUpperCase());
        Tenant tenant = tenantService.updateTenantStatus(id, status);
        return ResponseEntity.ok(tenant);
    }

    /**
     * List all users belonging to a specific tenant.
     */
    @GetMapping("/{id}/users")
    public ResponseEntity<List<PlatformUser>> getTenantUsers(@PathVariable Long id) {
        Tenant tenant = tenantService.getTenantById(id);
        List<PlatformUser> users = userService.getUsersByTenantKey(tenant.getTenantKey());
        return ResponseEntity.ok(users);
    }

    /**
     * Get platform-wide analytics summary.
     */
    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getPlatformAnalytics() {
        List<Tenant> allTenants = tenantService.getAllTenants();
        List<Tenant> activeTenants = tenantService.getActiveTenants();

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("totalTenants", allTenants.size());
        analytics.put("activeTenants", activeTenants.size());
        analytics.put("suspendedTenants",
                allTenants.stream()
                        .filter(t -> t.getStatus() == Tenant.TenantStatus.SUSPENDED)
                        .count());
        analytics.put("onboardingTenants",
                allTenants.stream()
                        .filter(t -> t.getStatus() == Tenant.TenantStatus.ONBOARDING)
                        .count());

        // Count users by role across all tenants
        Map<String, Long> usersByRole = new HashMap<>();
        for (PlatformUser.UserRole role : PlatformUser.UserRole.values()) {
            long count = allTenants.stream()
                    .flatMap(t -> userService.getUsersByTenantKey(t.getTenantKey()).stream())
                    .filter(u -> u.getRole() == role)
                    .count();
            usersByRole.put(role.name(), count);
        }
        analytics.put("usersByRole", usersByRole);

        // Total users
        long totalUsers = allTenants.stream()
                .mapToLong(t -> userService.getUsersByTenantKey(t.getTenantKey()).size())
                .sum();
        analytics.put("totalUsers", totalUsers);

        // Subscription plan breakdown
        Map<String, Long> tenantsByPlan = allTenants.stream()
                .collect(Collectors.groupingBy(Tenant::getSubscriptionPlan, Collectors.counting()));
        analytics.put("tenantsByPlan", tenantsByPlan);

        // Recent tenants (last 5)
        List<Tenant> recentTenants = allTenants.stream()
                .sorted(Comparator.comparing(Tenant::getCreatedAt).reversed())
                .limit(5)
                .collect(Collectors.toList());
        analytics.put("recentTenants", recentTenants.stream()
                .map(t -> Map.of(
                        "id", t.getId(),
                        "tenantKey", t.getTenantKey(),
                        "companyName", t.getCompanyName(),
                        "status", t.getStatus().name(),
                        "createdAt", t.getCreatedAt().toString()
                ))
                .collect(Collectors.toList()));

        return ResponseEntity.ok(analytics);
    }

    /**
     * Validate database connection for tenant onboarding.
     */
    @PostMapping("/validate-connection")
    public ResponseEntity<Map<String, Object>> validateDatabaseConnection(
            @RequestBody Map<String, String> connectionDetails) {
        String dbUrl = connectionDetails.get("dbUrl");
        String dbUsername = connectionDetails.get("dbUsername");
        String dbPassword = connectionDetails.get("dbPassword");

        Map<String, Object> result = tenantService.validateDatabaseConnection(
                dbUrl, dbUsername, dbPassword);

        return ResponseEntity.ok(result);
    }
}
