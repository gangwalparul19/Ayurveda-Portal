package com.ayurveda.platform.master.service;

import com.ayurveda.platform.config.DataSourceConfig;
import com.ayurveda.platform.dto.request.TenantOnboardRequest;
import com.ayurveda.platform.exception.TenantNotFoundException;
import com.ayurveda.platform.master.entity.Tenant;
import com.ayurveda.platform.master.entity.TenantUiConfig;
import com.ayurveda.platform.master.entity.PlatformUser;
import com.ayurveda.platform.master.repository.TenantRepository;
import com.ayurveda.platform.master.repository.TenantUiConfigRepository;
import com.ayurveda.platform.master.repository.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing tenants (vendors) in the platform.
 * Handles tenant CRUD, onboarding, and datasource registration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("dev")  // Only load in dev mode (multi-tenant)
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantUiConfigRepository uiConfigRepository;
    private final PlatformUserRepository userRepository;
    private final DataSourceConfig dataSourceConfig;
    private final PasswordEncoder passwordEncoder;

    /**
     * Retrieve all tenants.
     */
    public List<Tenant> getAllTenants() {
        return tenantRepository.findAll();
    }

    /**
     * Retrieve all active tenants.
     */
    public List<Tenant> getActiveTenants() {
        return tenantRepository.findAllByStatus(Tenant.TenantStatus.ACTIVE);
    }

    /**
     * Retrieve a tenant by its unique key.
     */
    public Tenant getTenantByKey(String tenantKey) {
        return tenantRepository.findByTenantKey(tenantKey)
                .orElseThrow(() -> new TenantNotFoundException(tenantKey, true));
    }

    /**
     * Retrieve a tenant by ID.
     */
    public Tenant getTenantById(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found with ID: " + id));
    }

    /**
     * Onboard a new tenant: create tenant record, UI config,
     * initial admin user, and register the datasource.
     */
    @Transactional
    public Tenant onboardTenant(TenantOnboardRequest request) {
        // Validate uniqueness
        if (tenantRepository.existsByTenantKey(request.getTenantKey())) {
            throw new IllegalArgumentException(
                    "Tenant key already exists: " + request.getTenantKey());
        }
        if (userRepository.existsByUsername(request.getAdminUsername())) {
            throw new IllegalArgumentException(
                    "Username already exists: " + request.getAdminUsername());
        }
        if (userRepository.existsByEmail(request.getAdminEmail())) {
            throw new IllegalArgumentException(
                    "Email already exists: " + request.getAdminEmail());
        }

        // Create tenant
        Tenant tenant = Tenant.builder()
                .tenantKey(request.getTenantKey())
                .companyName(request.getCompanyName())
                .dbUrl(request.getDbUrl())
                .dbUsername(request.getDbUsername())
                .dbPassword(request.getDbPassword())
                .domain(request.getDomain())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .subscriptionPlan(request.getSubscriptionPlan() != null
                        ? request.getSubscriptionPlan() : "BASIC")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        tenant = tenantRepository.save(tenant);

        // Create UI config
        TenantUiConfig uiConfig = TenantUiConfig.builder()
                .tenant(tenant)
                .primaryColor(request.getPrimaryColor() != null
                        ? request.getPrimaryColor() : "#2E7D32")
                .secondaryColor(request.getSecondaryColor() != null
                        ? request.getSecondaryColor() : "#1B5E20")
                .accentColor(request.getAccentColor() != null
                        ? request.getAccentColor() : "#FF9800")
                .logoUrl(request.getLogoUrl())
                .fontFamily(request.getFontFamily() != null
                        ? request.getFontFamily() : "Inter")
                .build();
        uiConfigRepository.save(uiConfig);

        // Create initial admin user
        PlatformUser adminUser = PlatformUser.builder()
                .tenant(tenant)
                .username(request.getAdminUsername())
                .email(request.getAdminEmail())
                .passwordHash(passwordEncoder.encode(request.getAdminPassword()))
                .role(PlatformUser.UserRole.TENANT_ADMIN)
                .fullName(request.getAdminFullName())
                .phone(request.getAdminPhone())
                .isActive(true)
                .build();
        userRepository.save(adminUser);

        // Register datasource for the new tenant
        dataSourceConfig.addTenantDataSource(tenant);

        log.info("Successfully onboarded tenant: {} ({})",
                tenant.getCompanyName(), tenant.getTenantKey());

        return tenant;
    }

    /**
     * Update tenant status (activate, suspend).
     */
    @Transactional
    public Tenant updateTenantStatus(Long tenantId, Tenant.TenantStatus newStatus) {
        Tenant tenant = getTenantById(tenantId);
        Tenant.TenantStatus oldStatus = tenant.getStatus();
        tenant.setStatus(newStatus);
        tenant = tenantRepository.save(tenant);

        if (newStatus == Tenant.TenantStatus.SUSPENDED) {
            dataSourceConfig.removeTenantDataSource(tenant.getTenantKey());
            log.info("Suspended tenant: {}", tenant.getTenantKey());
        } else if (newStatus == Tenant.TenantStatus.ACTIVE
                && oldStatus == Tenant.TenantStatus.SUSPENDED) {
            dataSourceConfig.addTenantDataSource(tenant);
            log.info("Reactivated tenant: {}", tenant.getTenantKey());
        }

        return tenant;
    }

    /**
     * Validate database connection before onboarding.
     */
    public Map<String, Object> validateDatabaseConnection(String dbUrl, String dbUsername, String dbPassword) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Try to create a test connection
            com.zaxxer.hikari.HikariDataSource testDs = new com.zaxxer.hikari.HikariDataSource();
            testDs.setJdbcUrl(dbUrl);
            testDs.setUsername(dbUsername);
            testDs.setPassword(dbPassword);
            testDs.setDriverClassName("com.mysql.cj.jdbc.Driver");
            testDs.setMaximumPoolSize(1);
            testDs.setConnectionTimeout(5000); // 5 seconds timeout
            
            try (java.sql.Connection conn = testDs.getConnection()) {
                // Test if connection is valid
                boolean isValid = conn.isValid(2);
                
                if (isValid) {
                    // Get database metadata
                    java.sql.DatabaseMetaData metaData = conn.getMetaData();
                    result.put("valid", true);
                    result.put("message", "Connection successful");
                    result.put("databaseProductName", metaData.getDatabaseProductName());
                    result.put("databaseProductVersion", metaData.getDatabaseProductVersion());
                    
                    log.info("Database connection validated successfully for URL: {}", dbUrl);
                } else {
                    result.put("valid", false);
                    result.put("message", "Connection established but not valid");
                }
            } finally {
                testDs.close();
            }
        } catch (Exception e) {
            result.put("valid", false);
            result.put("message", "Connection failed: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
            log.error("Database connection validation failed for URL: {}", dbUrl, e);
        }
        
        return result;
    }
}
