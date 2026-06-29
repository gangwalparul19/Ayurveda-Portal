package com.ayurveda.platform.config;

import com.ayurveda.platform.master.entity.Tenant;
import com.ayurveda.platform.master.repository.TenantRepository;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configures the multi-tenant datasource routing.
 *
 * On startup, reads all active tenants from the master database
 * and creates a connection pool for each tenant's dedicated database.
 * The TenantRoutingDataSource routes to the correct pool based on
 * the current TenantContext.
 */
@Configuration
@Slf4j
@Profile("dev")  // Only load in dev mode (multi-tenant), not in simple mode
public class DataSourceConfig {

    @Value("${spring.datasource.master.url}")
    private String masterUrl;

    @Value("${spring.datasource.master.username}")
    private String masterUsername;

    @Value("${spring.datasource.master.password}")
    private String masterPassword;

    @Value("${spring.datasource.master.driver-class-name}")
    private String masterDriverClassName;

    @Value("${app.tenant.datasource.hikari.maximum-pool-size:5}")
    private int tenantMaxPoolSize;

    @Value("${app.tenant.datasource.hikari.minimum-idle:2}")
    private int tenantMinIdle;

    private final Map<Object, Object> tenantDataSources = new ConcurrentHashMap<>();

    /**
     * Creates the master datasource used for tenant registry,
     * user authentication, and platform-level operations.
     */
    @Bean(name = "masterDataSource")
    public DataSource masterDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(masterUrl);
        ds.setUsername(masterUsername);
        ds.setPassword(masterPassword);
        ds.setDriverClassName(masterDriverClassName);
        ds.setPoolName("master-pool");
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(5);
        return ds;
    }

    /**
     * Creates the primary routing datasource.
     * Falls back to the master datasource when no tenant context is set.
     */
    @Bean
    @Primary
    @DependsOn("masterDataSource")
    public DataSource dataSource(@Qualifier("masterDataSource") DataSource masterDataSource) {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();
        routingDataSource.setDefaultTargetDataSource(masterDataSource);
        routingDataSource.setTargetDataSources(tenantDataSources);
        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }

    /**
     * Registers a new tenant datasource at runtime.
     * Called during tenant onboarding or when the application discovers
     * a tenant whose datasource is not yet initialized.
     *
     * @param tenant the tenant entity with DB connection details
     */
    public void addTenantDataSource(Tenant tenant) {
        if (tenantDataSources.containsKey(tenant.getTenantKey())) {
            log.info("Tenant datasource already registered: {}", tenant.getTenantKey());
            return;
        }

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(tenant.getDbUrl());
        ds.setUsername(tenant.getDbUsername());
        ds.setPassword(tenant.getDbPassword());
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setPoolName("tenant-" + tenant.getTenantKey());
        ds.setMaximumPoolSize(tenantMaxPoolSize);
        ds.setMinimumIdle(tenantMinIdle);

        tenantDataSources.put(tenant.getTenantKey(), ds);
        log.info("Registered datasource for tenant: {}", tenant.getTenantKey());
    }

    /**
     * Removes a tenant datasource (e.g., when a tenant is suspended).
     *
     * @param tenantKey the tenant key to remove
     */
    public void removeTenantDataSource(String tenantKey) {
        DataSource removed = (DataSource) tenantDataSources.remove(tenantKey);
        if (removed instanceof HikariDataSource hikariDs) {
            hikariDs.close();
            log.info("Closed and removed datasource for tenant: {}", tenantKey);
        }
    }

    /**
     * Returns the current map of tenant datasources (for re-initialization).
     */
    public Map<Object, Object> getTenantDataSources() {
        return tenantDataSources;
    }
}
