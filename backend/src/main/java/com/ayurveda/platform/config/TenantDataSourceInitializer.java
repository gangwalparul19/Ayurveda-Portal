package com.ayurveda.platform.config;

import com.ayurveda.platform.master.entity.Tenant;
import com.ayurveda.platform.master.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Initializes tenant datasources on application startup.
 * Reads all active tenants from the master database and registers
 * their datasources with the routing datasource.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile("dev")  // Only load in dev mode (multi-tenant), not in simple mode
public class TenantDataSourceInitializer {

    private final TenantRepository tenantRepository;
    private final DataSourceConfig dataSourceConfig;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeTenantDataSources() {
        log.info("Initializing tenant datasources...");

        List<Tenant> activeTenants = tenantRepository
                .findAllByStatus(Tenant.TenantStatus.ACTIVE);

        for (Tenant tenant : activeTenants) {
            try {
                dataSourceConfig.addTenantDataSource(tenant);
                log.info("Initialized datasource for tenant: {} ({})",
                        tenant.getCompanyName(), tenant.getTenantKey());
            } catch (Exception e) {
                log.error("Failed to initialize datasource for tenant: {} - {}",
                        tenant.getTenantKey(), e.getMessage());
            }
        }

        log.info("Tenant datasource initialization complete. {} active tenant(s) loaded.",
                activeTenants.size());
    }
}
