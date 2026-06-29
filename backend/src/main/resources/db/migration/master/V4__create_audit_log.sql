-- =====================================================
-- V4: Create audit_log table
-- Platform-level audit trail for admin actions
-- =====================================================

CREATE TABLE IF NOT EXISTS audit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT,
    user_id     BIGINT,
    action      VARCHAR(100) NOT NULL,
    details     JSON,
    ip_address  VARCHAR(45),
    timestamp   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_audit_tenant (tenant_id),
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_timestamp (timestamp),
    INDEX idx_audit_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
