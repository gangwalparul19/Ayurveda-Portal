package com.ayurveda.platform.integration;

import com.ayurveda.platform.config.TenantRoutingDataSource;
import com.ayurveda.platform.security.JwtAuthenticationFilter;
import com.ayurveda.platform.security.JwtTokenProvider;
import com.ayurveda.platform.security.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for multi-tenant data isolation (Task 30.2).
 *
 * <h2>What this test verifies</h2>
 * This test exercises the real tenant-routing wiring that enforces isolation in
 * the multi-tenant ("dev") runtime, using the live Spring beans:
 * <ul>
 *   <li>{@link JwtTokenProvider} mints access tokens carrying a {@code tenant_key} claim.</li>
 *   <li>{@link JwtAuthenticationFilter} extracts that claim from the {@code Authorization}
 *       header and binds it to the per-request {@link TenantContext} (a ThreadLocal).</li>
 *   <li>{@link TenantRoutingDataSource#determineCurrentLookupKey()} resolves the datasource
 *       routing key from {@link TenantContext} — this is the exact decision that, in the
 *       multi-tenant runtime, sends each tenant's queries to its own dedicated database.</li>
 *   <li>The filter clears {@link TenantContext} after each request, preventing one tenant's
 *       routing key from leaking into a subsequent request on a pooled (reused) thread.</li>
 * </ul>
 *
 * <p>By asserting that two different tenants resolve to two <em>different, non-null</em>
 * routing keys (and that an unauthenticated request resolves to {@code null} → the default
 * master datasource), the test demonstrates the mechanism that prevents Tenant 2 from being
 * routed to Tenant 1's database, which is how cross-tenant data leakage is prevented.
 *
 * <h2>What this test does NOT verify, and why</h2>
 * It does <strong>not</strong> assert physical "Tenant 2 cannot read rows Tenant 1 wrote"
 * against a live datasource. That cannot be honestly exercised in this test environment:
 * <ul>
 *   <li>The per-tenant connection pools and the {@link TenantRoutingDataSource} bean are wired
 *       only by {@code DataSourceConfig}, which is annotated {@code @Profile("dev")} and
 *       requires one dedicated MySQL database per tenant.</li>
 *   <li>The {@code test} profile (see {@code application-test.yml}) uses a <em>single</em>
 *       in-memory H2 datasource with no routing. Under that single datasource, rows written
 *       "as Tenant 1" physically live in the same database that "Tenant 2" would read, so any
 *       assertion that Tenant 2 cannot see Tenant 1's rows would be a false positive — it would
 *       pass for the wrong reason (the test profile has no routing) and would not reflect the
 *       production isolation guarantee. This test deliberately avoids fabricating that behaviour.</li>
 *   <li>The project is additionally mid-migration toward a single-client, configuration-based
 *       architecture (see MIGRATION_TO_SINGLE_CLIENT.md); Requirement 17 has been redefined as
 *       single-client Configuration Management. Physical multi-database isolation is therefore
 *       a dev-profile-only concern, validated here at the routing-decision level.</li>
 * </ul>
 *
 * <p>Validates: Requirements 17.1-17.5 (to the extent the test environment supports —
 * the tenant-context/routing wiring that backs data isolation).
 */
@SpringBootTest
@ActiveProfiles("test")
class TenantIsolationIntegrationTest {

    private static final String TENANT_ONE = "vendor_one";
    private static final String TENANT_TWO = "vendor_two";

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Subclass that exposes the protected routing-key resolution so the test can assert on it.
     * This is the production {@link TenantRoutingDataSource} logic verbatim.
     */
    private static class ExposedRoutingDataSource extends TenantRoutingDataSource {
        Object resolveLookupKey() {
            return determineCurrentLookupKey();
        }
    }

    private final ExposedRoutingDataSource routingDataSource = new ExposedRoutingDataSource();

    @BeforeEach
    void clearContextBefore() {
        TenantContext.clear();
    }

    @AfterEach
    void clearContextAfter() {
        TenantContext.clear();
    }

    /**
     * The routing datasource resolves its lookup key directly from {@link TenantContext}.
     * Two distinct tenants yield two distinct, non-null keys (→ distinct tenant databases),
     * and an unset context yields {@code null} (→ the default master datasource).
     * Validates Requirements 17.1-17.5 (routing decision that backs isolation).
     */
    @Test
    @DisplayName("Routing key tracks the current tenant context and differs per tenant")
    void routingKeyTracksTenantContext() {
        assertThat(routingDataSource.resolveLookupKey())
                .as("no tenant context → route to default/master datasource")
                .isNull();

        TenantContext.setTenantKey(TENANT_ONE);
        Object keyForTenantOne = routingDataSource.resolveLookupKey();

        TenantContext.setTenantKey(TENANT_TWO);
        Object keyForTenantTwo = routingDataSource.resolveLookupKey();

        assertThat(keyForTenantOne).isEqualTo(TENANT_ONE);
        assertThat(keyForTenantTwo).isEqualTo(TENANT_TWO);
        assertThat(keyForTenantOne)
                .as("different tenants must route to different datasources")
                .isNotEqualTo(keyForTenantTwo);
    }

    /**
     * End-to-end JWT path: a request authenticated as Tenant 1 is bound to Tenant 1's routing
     * key, and a request authenticated as Tenant 2 is bound to Tenant 2's routing key. Because
     * the two requests resolve to different datasources, Tenant 2's queries can never reach
     * Tenant 1's database. Validates Requirements 17.1-17.5.
     */
    @Test
    @DisplayName("JWT filter binds each tenant's request to its own routing key")
    void jwtFilterBindsRequestToTenantRoutingKey() throws ServletException, IOException {
        String tenantOneToken = jwtTokenProvider.generateAccessToken(
                "alice", TENANT_ONE, 1L, 10L, "Alice One", "TENANT_ADMIN");
        String tenantTwoToken = jwtTokenProvider.generateAccessToken(
                "bob", TENANT_TWO, 2L, 20L, "Bob Two", "TENANT_ADMIN");

        Object keyDuringTenantOneRequest = runFilterAndCaptureRoutingKey(tenantOneToken);
        Object keyDuringTenantTwoRequest = runFilterAndCaptureRoutingKey(tenantTwoToken);

        assertThat(keyDuringTenantOneRequest).isEqualTo(TENANT_ONE);
        assertThat(keyDuringTenantTwoRequest).isEqualTo(TENANT_TWO);
        assertThat(keyDuringTenantOneRequest)
                .as("Tenant 2's request must not be routed to Tenant 1's datasource")
                .isNotEqualTo(keyDuringTenantTwoRequest);
    }

    /**
     * After a tenant's request completes, {@link TenantContext} is cleared so a subsequent
     * request on a reused (pooled) thread does not inherit the previous tenant's routing key.
     * This prevents cross-tenant data leakage across requests. Validates Requirements 17.1-17.5.
     */
    @Test
    @DisplayName("Tenant context is cleared after the request to prevent cross-request leakage")
    void tenantContextClearedAfterRequest() throws ServletException, IOException {
        String tenantOneToken = jwtTokenProvider.generateAccessToken(
                "alice", TENANT_ONE, 1L, 10L, "Alice One", "TENANT_ADMIN");

        runFilterAndCaptureRoutingKey(tenantOneToken);

        assertThat(TenantContext.getTenantKey())
                .as("context must be cleared after the request finishes")
                .isNull();
        assertThat(routingDataSource.resolveLookupKey())
                .as("a fresh request with no token routes to the default datasource, not Tenant 1's")
                .isNull();
    }

    /**
     * A request without a bearer token leaves no tenant bound, so it resolves to the default
     * (master) datasource rather than any tenant database. Validates Requirements 17.1-17.5.
     */
    @Test
    @DisplayName("Unauthenticated request resolves to the default datasource (no tenant)")
    void unauthenticatedRequestHasNoTenantContext() throws ServletException, IOException {
        Object keyDuringRequest = runFilterAndCaptureRoutingKey(null);

        assertThat(keyDuringRequest)
                .as("no token → no tenant routing key")
                .isNull();
    }

    /**
     * Runs the real {@link JwtAuthenticationFilter} for a request carrying the given token
     * (or no token when {@code null}) and captures the routing key that
     * {@link TenantRoutingDataSource} would resolve at the moment the request is being handled.
     */
    private Object runFilterAndCaptureRoutingKey(String bearerToken)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/orders");
        if (bearerToken != null) {
            request.addHeader("Authorization", "Bearer " + bearerToken);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain(routingDataSource);

        jwtAuthenticationFilter.doFilter(request, response, chain);

        return chain.capturedRoutingKey;
    }

    /**
     * Captures the datasource routing key that the {@link TenantRoutingDataSource} resolves
     * while the (simulated) request is in flight — i.e., while {@link TenantContext} is set.
     */
    private static class CapturingFilterChain implements FilterChain {
        private final ExposedRoutingDataSource routingDataSource;
        private Object capturedRoutingKey;

        CapturingFilterChain(ExposedRoutingDataSource routingDataSource) {
            this.routingDataSource = routingDataSource;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            this.capturedRoutingKey = routingDataSource.resolveLookupKey();
        }
    }
}
