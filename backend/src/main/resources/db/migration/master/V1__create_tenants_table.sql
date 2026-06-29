-- =====================================================
-- V1: Create tenants table
-- Master database: stores vendor/tenant registry
-- =====================================================

CREATE TABLE IF NOT EXISTS tenants (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_key      VARCHAR(50)  NOT NULL UNIQUE,
    company_name    VARCHAR(255) NOT NULL,
    db_url          VARCHAR(500) NOT NULL,
    db_username     VARCHAR(100) NOT NULL,
    db_password     VARCHAR(255) NOT NULL,
    domain          VARCHAR(255),
    status          ENUM('ACTIVE', 'SUSPENDED', 'ONBOARDING') NOT NULL DEFAULT 'ONBOARDING',
    subscription_plan VARCHAR(50) DEFAULT 'BASIC',
    contact_email   VARCHAR(255),
    contact_phone   VARCHAR(20),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_tenants_status (status),
    INDEX idx_tenants_domain (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
