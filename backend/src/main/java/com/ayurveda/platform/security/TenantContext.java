package com.ayurveda.platform.security;

/**
 * ThreadLocal holder for the current tenant context.
 * Set by TenantContextFilter on each request and cleared after processing.
 * Used by TenantRoutingDataSource to determine which database to route to.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // Utility class — no instantiation
    }

    /**
     * Set the tenant key for the current thread.
     *
     * @param tenantKey the unique tenant identifier (e.g., "vendor_shifa")
     */
    public static void setTenantKey(String tenantKey) {
        CURRENT_TENANT.set(tenantKey);
    }

    /**
     * Get the tenant key for the current thread.
     *
     * @return the current tenant key, or null if not set
     */
    public static String getTenantKey() {
        return CURRENT_TENANT.get();
    }

    /**
     * Clear the tenant context for the current thread.
     * Must be called in a finally block to prevent ThreadLocal leaks.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
