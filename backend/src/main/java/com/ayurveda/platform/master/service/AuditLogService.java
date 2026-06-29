package com.ayurveda.platform.master.service;

import com.ayurveda.platform.master.entity.AuditLog;
import com.ayurveda.platform.master.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Centralized audit logging service.
 *
 * Persists significant system actions (authentication, order, and payment
 * events) to the {@link AuditLog} entity so they can be reviewed for security
 * and operational auditing (Requirement 32).
 *
 * <p>This service is intentionally generic so it can be shared across feature
 * areas (e.g. authentication audit logging and order/payment audit logging).
 * Callers supply a stable {@code action} string and optional JSON {@code details}.
 *
 * <p>Audit writes run in an independent transaction (REQUIRES_NEW) so that a
 * failure to persist an audit entry never marks the caller's business
 * transaction for rollback. Callers should still treat audit logging as a
 * best-effort, non-critical side effect.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    // ===== Common action constants (shared, stable identifiers) =====
    // Authentication actions (used by authentication audit logging) are kept
    // generic here so they can be reused without modifying this service.
    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String ORDER_STATUS_CHANGED = "ORDER_STATUS_CHANGED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String ORDER_RETURNED = "ORDER_RETURNED";
    public static final String PAYMENT_RECORDED = "PAYMENT_RECORDED";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Core generic audit recording method.
     *
     * @param tenantId  optional tenant identifier (may be {@code null})
     * @param userId    the user who performed the action (may be {@code null} for
     *                  anonymous/public actions)
     * @param action    a stable, non-null action identifier (e.g. {@code ORDER_CREATED})
     * @param details   optional JSON string with additional context
     * @param ipAddress optional client IP address
     * @return the persisted {@link AuditLog}, or {@code null} if persistence failed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(Long tenantId, Long userId, String action, String details, String ipAddress) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Audit action must not be null or blank");
        }

        AuditLog entry = AuditLog.builder()
                .tenantId(tenantId)
                .userId(userId)
                .action(action)
                .details(details)
                .ipAddress(ipAddress)
                .build();

        AuditLog saved = auditLogRepository.save(entry);
        log.debug("Audit logged: action={}, userId={}, tenantId={}", action, userId, tenantId);
        return saved;
    }

    /**
     * Convenience overload for actions without tenant/IP context.
     */
    public AuditLog record(Long userId, String action, String details) {
        return record(null, userId, action, details, null);
    }

    /**
     * Convenience overload that serializes a details map to JSON.
     *
     * @param userId  the user who performed the action (nullable)
     * @param action  a stable, non-null action identifier
     * @param details key/value context that will be serialized to JSON
     */
    public AuditLog record(Long userId, String action, Map<String, Object> details) {
        return record(null, userId, action, toJson(details), null);
    }

    /**
     * Serialize a details map to a JSON string. Falls back to {@code String.valueOf}
     * if serialization fails so that audit logging never throws on formatting.
     */
    private String toJson(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception e) {
            log.warn("Failed to serialize audit details to JSON: {}", e.getMessage());
            return String.valueOf(details);
        }
    }
}
