package com.ayurveda.platform.config;

import com.ayurveda.platform.security.TenantContext;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.StringJoiner;

/**
 * Enables Spring's declarative caching for frequently accessed, rarely changing
 * data (Requirement 30: System Performance). With no dedicated cache provider on
 * the classpath, Spring Boot auto-configures a simple in-memory
 * {@code ConcurrentMapCacheManager}; caches are created on demand by name.
 *
 * <p>Because the application still routes per-tenant datasources via
 * {@link TenantContext}, all cache keys are prefixed with the current tenant key
 * so cached values are never shared across tenants.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache holding the distinct product category list (read often, changes rarely). */
    public static final String PRODUCT_CATEGORIES_CACHE = "productCategories";

    /**
     * Tenant-aware cache key generator. Prefixes every key with the current
     * tenant key, then appends the target method name and any arguments, so that
     * entries are isolated per tenant and per query method.
     */
    @Bean("tenantAwareKeyGenerator")
    public KeyGenerator tenantAwareKeyGenerator() {
        return (target, method, params) -> {
            StringJoiner key = new StringJoiner(":");
            String tenantKey = TenantContext.getTenantKey();
            key.add(tenantKey != null ? tenantKey : "_default");
            key.add(method.getName());
            for (Object param : params) {
                key.add(param != null ? param.toString() : "null");
            }
            return key.toString();
        };
    }
}
