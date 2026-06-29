package com.ayurveda.platform.master.service;

import com.ayurveda.platform.master.entity.AuditLog;
import com.ayurveda.platform.master.repository.AuditLogRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Size;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property-Based Tests for Audit Trail Completeness using jqwik.
 *
 * <p><b>Property 7: Audit Trail Completeness</b>
 *
 * <p><b>Validates: Requirements 6.1, 6.2, 6.3, 32.4</b>
 *
 * <p>Significant actions are recorded through
 * {@link AuditLogService#record} which persists an {@link AuditLog} entry.
 * For audit data to be useful for security and operational review, every
 * persisted entry must be complete: it must always carry a non-blank action,
 * preserve the user that performed the action (where one was supplied), and
 * record a creation timestamp. The recorded action must also match the action
 * that was requested - audit entries must never be silently dropped or
 * relabelled.
 *
 * <p>These invariants are verified across many generated
 * action / user / tenant / details combinations. The {@link AuditLogRepository}
 * is mocked (mirroring {@code AuditLogServiceTest}) to capture the entity that
 * would be persisted. The mock's {@code save} simulates the persistence layer's
 * {@code @CreationTimestamp} behaviour by stamping the creation time, so the
 * completeness of the captured entity reflects what the database would store.
 */
class AuditTrailCompletenessPropertyTest {

    /**
     * Significant, stable action identifiers recorded by the platform.
     * Mirrors the constants exposed by {@link AuditLogService} plus a couple of
     * representative authentication actions, since the service is intentionally
     * generic across feature areas.
     */
    private static final String[] SIGNIFICANT_ACTIONS = {
            AuditLogService.ORDER_CREATED,
            AuditLogService.ORDER_STATUS_CHANGED,
            AuditLogService.ORDER_CANCELLED,
            AuditLogService.ORDER_RETURNED,
            AuditLogService.PAYMENT_RECORDED,
            "USER_LOGIN",
            "LOGIN_FAILED"
    };

    /**
     * **Validates: Requirements 6.1, 6.2, 6.3, 32.4**
     *
     * Property 7: Audit Trail Completeness (core record path).
     *
     * For any significant action recorded via the core
     * {@code record(tenantId, userId, action, details, ipAddress)} method:
     * <ol>
     *   <li>exactly one audit entry is persisted (no entry is dropped);</li>
     *   <li>the persisted action is non-null and non-blank;</li>
     *   <li>the persisted action equals the requested action;</li>
     *   <li>the recorded user is preserved (including a supplied user id);</li>
     *   <li>the recorded tenant is preserved;</li>
     *   <li>a creation timestamp is populated.</li>
     * </ol>
     */
    @Property(tries = 500)
    @Label("Property 7: Audit Trail Completeness - core record() always persists a complete entry")
    void auditTrailCompleteness_coreRecord(
            @ForAll("significantActions") String action,
            @ForAll("optionalIds") Long userId,
            @ForAll("optionalIds") Long tenantId,
            @ForAll @Size(max = 5) Map<String, String> details,
            @ForAll("optionalIpAddresses") String ipAddress
    ) {
        // Arrange: fresh mock per try so captured state is isolated.
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenAnswer(inv -> persistSimulating(inv.getArgument(0)));
        AuditLogService auditLogService = new AuditLogService(auditLogRepository);

        String detailsJson = details.isEmpty() ? null : details.toString();

        // Act
        auditLogService.record(tenantId, userId, action, detailsJson, ipAddress);

        // Assert: a single complete entry was persisted.
        AuditLog saved = captureSingleSave(auditLogRepository);

        assertThat(saved.getAction())
                .as("persisted action must be non-null and non-blank")
                .isNotNull()
                .isNotBlank();
        assertThat(saved.getAction())
                .as("recorded action must match the requested action")
                .isEqualTo(action);
        assertThat(saved.getUserId())
                .as("recorded user must be preserved exactly as supplied")
                .isEqualTo(userId);
        assertThat(saved.getTenantId())
                .as("recorded tenant must be preserved exactly as supplied")
                .isEqualTo(tenantId);
        assertThat(saved.getTimestamp())
                .as("persisted entry must carry a creation timestamp")
                .isNotNull();
    }

    /**
     * **Validates: Requirements 6.1, 6.2, 6.3, 32.4**
     *
     * Property 7: Audit Trail Completeness (map convenience overload).
     *
     * The map overload {@code record(userId, action, detailsMap)} is the path
     * used by business services (e.g. OrderService). It must produce an equally
     * complete entry: a single persisted record whose action matches the request,
     * whose user is preserved, and which carries a creation timestamp - regardless
     * of the generated details map (including empty maps).
     */
    @Property(tries = 500)
    @Label("Property 7: Audit Trail Completeness - map overload always persists a complete entry")
    void auditTrailCompleteness_mapOverload(
            @ForAll("significantActions") String action,
            @ForAll("optionalIds") Long userId,
            @ForAll @Size(max = 5) Map<String, String> rawDetails
    ) {
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenAnswer(inv -> persistSimulating(inv.getArgument(0)));
        AuditLogService auditLogService = new AuditLogService(auditLogRepository);

        Map<String, Object> details = new LinkedHashMap<>(rawDetails);

        auditLogService.record(userId, action, details);

        AuditLog saved = captureSingleSave(auditLogRepository);

        assertThat(saved.getAction())
                .as("persisted action must be non-null and non-blank")
                .isNotNull()
                .isNotBlank();
        assertThat(saved.getAction())
                .as("recorded action must match the requested action")
                .isEqualTo(action);
        assertThat(saved.getUserId())
                .as("recorded user must be preserved exactly as supplied")
                .isEqualTo(userId);
        assertThat(saved.getTimestamp())
                .as("persisted entry must carry a creation timestamp")
                .isNotNull();
    }

    // ===== Helpers =====

    /**
     * Simulates the persistence layer: Hibernate populates {@code timestamp} via
     * {@code @CreationTimestamp} at persist time. The repository mock reproduces
     * that side effect so captured entities reflect the persisted state.
     */
    private static AuditLog persistSimulating(AuditLog entry) {
        if (entry.getTimestamp() == null) {
            entry.setTimestamp(LocalDateTime.now());
        }
        return entry;
    }

    private static AuditLog captureSingleSave(AuditLogRepository repository) {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository, times(1)).save(captor.capture());
        return captor.getValue();
    }

    // ===== Generators =====

    @Provide
    Arbitrary<String> significantActions() {
        return Arbitraries.of(SIGNIFICANT_ACTIONS);
    }

    /** Identifiers that may be absent (anonymous/public actions) ~10% of the time. */
    @Provide
    Arbitrary<Long> optionalIds() {
        return Arbitraries.longs().between(1L, 1_000_000L).injectNull(0.1);
    }

    @Provide
    Arbitrary<String> optionalIpAddresses() {
        Arbitrary<String> ipv4 = Arbitraries.integers().between(0, 255)
                .list().ofSize(4)
                .map(octets -> octets.stream().map(String::valueOf)
                        .reduce((a, b) -> a + "." + b).orElse("0.0.0.0"));
        return ipv4.injectNull(0.3);
    }
}
