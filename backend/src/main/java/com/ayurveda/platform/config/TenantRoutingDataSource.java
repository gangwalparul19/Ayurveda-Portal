package com.ayurveda.platform.config;

import com.ayurveda.platform.security.TenantContext;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Custom DataSource implementation that routes database connections
 * based on the current tenant context (ThreadLocal).
 *
 * The lookup key is the tenant_key string (e.g., "vendor_shifa").
 * If no tenant is set, it falls back to the default (master) datasource.
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getTenantKey();
    }
}
