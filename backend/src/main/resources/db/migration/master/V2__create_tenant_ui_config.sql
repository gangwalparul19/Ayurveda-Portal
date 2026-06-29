-- =====================================================
-- V2: Create tenant_ui_config table
-- Stores branding/theme configuration per tenant
-- =====================================================

CREATE TABLE IF NOT EXISTS tenant_ui_config (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT NOT NULL UNIQUE,
    primary_color       VARCHAR(7) DEFAULT '#2E7D32',
    secondary_color     VARCHAR(7) DEFAULT '#1B5E20',
    accent_color        VARCHAR(7) DEFAULT '#FF9800',
    logo_url            VARCHAR(500),
    favicon_url         VARCHAR(500),
    font_family         VARCHAR(100) DEFAULT 'Inter',
    custom_css          TEXT,
    storefront_enabled  BOOLEAN DEFAULT FALSE,
    storefront_config   JSON,

    CONSTRAINT fk_ui_config_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
