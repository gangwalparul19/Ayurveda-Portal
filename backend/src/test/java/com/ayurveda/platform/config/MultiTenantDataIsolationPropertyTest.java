package com.ayurveda.platform.config;

import com.ayurveda.platform.security.TenantContext;
import net.jqwik.api.*;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Property-Based Tests for Multi-Tenant Data Isolation (routing/context layer) using jqwik.
 *
 * <p><b>Property 9: Multi-Tenant Data Isolation</b><br>
 * <b>Validates: Requirements 17.1, 17.2, 17.3, 17.4, 17.5</b> (original multi-tenant requirement)
 *
 * <h2>Architectural context (IMPORTANT)</h2>
 * Per {@code MIGRATION_TO_SINGLE_CLIENT.md}, this system has been migrated from a multi-tenant
 * SaaS platform to a single-client, configuration-based application. Requirement 17 in the
 * current {@code requirements.md} has been re-purposed as "Configuration Management", and the
 * migration document explicitly lists "Cross-tenant data isolation validation" and
 * "TenantContext / ThreadLocal management" among the tasks that were removed. The
 * {@code TenantDataSourceInitializer} is gated behind the {@code dev} profile and is inert in the
 * {@code simple} (single-client) profile.
 *
 * <p>Consequently, the end-to-end "no cross-tenant data leakage across separate databases"
 * property from the design ({@code query(T1) ∩ query(T2) = ∅}) is <em>no longer applicable</em>
 * to the product as shipped and cannot be validated without resurrecting a multi-database setup.
 * Rather than fabricate that behavior, this test validates the strongest property that the
 * <em>still-present</em> routing/context code must satisfy:
 *
 * <pre>
 *   TenantContext.getTenantKey() = T  ⟹  TenantRoutingDataSource.determineCurrentLookupKey() = T
 * </pre>
 *
 * and the thread-confinement guarantee that underpins isolation:
 *
 * <pre>
 *   A tenant key set on thread A is never observable on thread B.
 * </pre>
 *
 * These are exactly the unit-testable foundations of Property 9. The cross-database query
 * disjointness sub-property is documented here as not validatable in the single-client
 * architecture (see {@code MIGRATION_TO_SINGLE_CLIENT.md}).
 */
class MultiTenantDataIsolationPropertyTest {

    private final TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();

    /**
     * **Validates: Requirements 17.1, 17.2**
     *
     * Property: The routing key resolved by the datasource always equals the tenant key currently
     * held in the context. After {@code clear()} the resolved key is {@code null}, which causes
     * {@link org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource} to fall back to
     * the default (master) datasource.
     *
     * <p>The test replays an arbitrary, interleaved sequence of SET / CLEAR operations and, after
     * every step, asserts the routing key matches a simple reference model of "the last key set
     * since the most recent clear".
     */
    @Property(tries = 1000)
    @Label("Routing key equals current tenant context across arbitrary set/clear interleavings")
    void routingKeyTracksTenantContext(@ForAll("tenantOperations") List<TenantOperation> operations) {
        try {
            // Reference model of the expected current tenant key (null == no tenant / default DS).
            String expectedTenantKey = null;

            for (TenantOperation op : operations) {
                if (op.isClear()) {
                    TenantContext.clear();
                    expectedTenantKey = null;
                } else {
                    TenantContext.setTenantKey(op.tenantKey());
                    expectedTenantKey = op.tenantKey();
                }

                // The context must report exactly what we asked for.
                String contextKey = TenantContext.getTenantKey();
                assert Objects.equals(contextKey, expectedTenantKey) :
                        String.format("TenantContext key mismatch: expected %s, got %s",
                                expectedTenantKey, contextKey);

                // Property 9 core: routing key == current tenant context.
                Object lookupKey = routingDataSource.determineCurrentLookupKey();
                assert Objects.equals(lookupKey, expectedTenantKey) :
                        String.format("Routing lookup key mismatch: context=%s, lookupKey=%s",
                                expectedTenantKey, lookupKey);
            }
        } finally {
            // Never leak tenant state into the next jqwik try (threads are reused).
            TenantContext.clear();
        }
    }

    /**
     * **Validates: Requirements 17.3, 17.4, 17.5**
     *
     * Property: {@code clear()} is absorbing — regardless of what was set before, immediately after
     * a clear the resolved routing key is {@code null} (fall back to default datasource). This is
     * the safety property that prevents a leaked ThreadLocal from accidentally routing a subsequent,
     * tenant-less request to a previously-used tenant database.
     */
    @Property(tries = 500)
    @Label("After clear() the routing key is always null regardless of prior tenant")
    void clearAlwaysResetsRoutingKey(@ForAll("tenantKeys") String tenantKey) {
        try {
            TenantContext.setTenantKey(tenantKey);
            assert tenantKey.equals(routingDataSource.determineCurrentLookupKey()) :
                    "Precondition failed: routing key should equal the set tenant key";

            TenantContext.clear();

            assert TenantContext.getTenantKey() == null :
                    "TenantContext.getTenantKey() must be null after clear()";
            assert routingDataSource.determineCurrentLookupKey() == null :
                    "Routing lookup key must be null after clear() (falls back to default datasource)";
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * **Validates: Requirements 17.1, 17.2, 17.3 (thread confinement underpinning isolation)**
     *
     * Property (no cross-tenant leakage at the context layer): for any two distinct tenant keys
     * assigned to two concurrent threads, neither thread ever observes the other's tenant key via
     * the routing datasource. This is the ThreadLocal confinement guarantee on which all higher
     * level data isolation depends.
     *
     * <p>Each worker thread sets its own tenant key, synchronizes at a barrier (so both threads have
     * set their context simultaneously), then repeatedly resolves the routing key and asserts it
     * only ever sees its own key.
     */
    @Property(tries = 200)
    @Label("Tenant context is thread-confined: concurrent threads never observe each other's tenant")
    void tenantContextIsThreadConfined(
            @ForAll("tenantKeys") String keyA,
            @ForAll("tenantKeys") String keyB
    ) throws Exception {
        Assume.that(!keyA.equals(keyB));

        final int probes = 50;
        final CyclicBarrier barrier = new CyclicBarrier(2);
        final CountDownLatch done = new CountDownLatch(2);
        final AtomicReference<String> failure = new AtomicReference<>(null);

        Thread threadA = new Thread(makeWorker(keyA, barrier, done, failure, probes), "tenant-A");
        Thread threadB = new Thread(makeWorker(keyB, barrier, done, failure, probes), "tenant-B");

        threadA.start();
        threadB.start();
        done.await();
        threadA.join();
        threadB.join();

        assert failure.get() == null : "Cross-thread tenant leakage detected: " + failure.get();
    }

    private Runnable makeWorker(String myKey,
                                CyclicBarrier barrier,
                                CountDownLatch done,
                                AtomicReference<String> failure,
                                int probes) {
        return () -> {
            try {
                TenantContext.setTenantKey(myKey);
                // Ensure both threads have established their context before probing.
                barrier.await();
                for (int i = 0; i < probes; i++) {
                    Object resolved = routingDataSource.determineCurrentLookupKey();
                    if (!myKey.equals(resolved)) {
                        failure.compareAndSet(null, String.format(
                                "thread expected its own key '%s' but resolved '%s'", myKey, resolved));
                        return;
                    }
                    Thread.yield();
                }
            } catch (Exception e) {
                failure.compareAndSet(null, "worker exception: " + e);
            } finally {
                TenantContext.clear();
                done.countDown();
            }
        };
    }

    // ============== Arbitraries (Generators) ==============

    /**
     * Realistic-ish tenant keys: the migration doc and code use forms like "vendor_shifa".
     * We also include arbitrary non-blank strings to stress the routing key handling across
     * unusual but legal map keys.
     */
    @Provide
    Arbitrary<String> tenantKeys() {
        Arbitrary<String> realistic = Arbitraries.of(
                "vendor_shifa", "vendor_ayur", "tenant_001", "tenant_002",
                "acme_health", "default", "master", "vendor-x", "VENDOR_UPPER");

        Arbitrary<String> arbitrary = Arbitraries.strings()
                .alpha().numeric().withChars('_', '-')
                .ofMinLength(1).ofMaxLength(40);

        return Arbitraries.oneOf(realistic, arbitrary);
    }

    /**
     * A single context operation: either CLEAR or SET(tenantKey).
     */
    @Provide
    Arbitrary<TenantOperation> tenantOperation() {
        Arbitrary<TenantOperation> clears = Arbitraries.just(TenantOperation.clearOp());
        Arbitrary<TenantOperation> sets = tenantKeys().map(TenantOperation::setOp);
        // Bias slightly toward sets so sequences exercise routing more than clearing.
        return Arbitraries.frequencyOf(
                Tuple.of(3, sets),
                Tuple.of(1, clears));
    }

    /**
     * Arbitrary interleaved sequences of SET / CLEAR operations.
     */
    @Provide
    Arbitrary<List<TenantOperation>> tenantOperations() {
        return tenantOperation().list().ofMinSize(1).ofMaxSize(30);
    }

    /**
     * Value type for a generated tenant-context operation.
     */
    static final class TenantOperation {
        private final boolean clear;
        private final String tenantKey;

        private TenantOperation(boolean clear, String tenantKey) {
            this.clear = clear;
            this.tenantKey = tenantKey;
        }

        static TenantOperation clearOp() {
            return new TenantOperation(true, null);
        }

        static TenantOperation setOp(String key) {
            return new TenantOperation(false, key);
        }

        boolean isClear() {
            return clear;
        }

        String tenantKey() {
            return tenantKey;
        }

        @Override
        public String toString() {
            return clear ? "CLEAR" : "SET(" + tenantKey + ")";
        }
    }
}
