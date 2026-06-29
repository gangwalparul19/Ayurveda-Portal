-- =====================================================
-- V3: Create platform_users table
-- Users across all tenants + super-admins
-- =====================================================

CREATE TABLE IF NOT EXISTS platform_users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT,
    username        VARCHAR(100) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            ENUM('SUPER_ADMIN', 'TENANT_ADMIN', 'MANAGER', 'SALESPERSON', 'DISPATCHER') NOT NULL,
    full_name       VARCHAR(255),
    phone           VARCHAR(20),
    is_active       BOOLEAN DEFAULT TRUE,
    last_login_at   TIMESTAMP NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_users_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id)
        ON DELETE SET NULL,

    INDEX idx_users_tenant (tenant_id),
    INDEX idx_users_role (role),
    INDEX idx_users_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
