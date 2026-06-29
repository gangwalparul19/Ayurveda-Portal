-- =====================================================
-- V6: Create company_config table
-- Company-specific settings and business rules
-- (name, address, logo, tax rates, thresholds, feature toggles).
-- Backs CompanyConfig entity / ConfigurationService.
-- Requirements: 17.1, 17.2, 17.3, 17.4, 17.5
-- =====================================================

CREATE TABLE IF NOT EXISTS company_config (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Company information
    company_name             VARCHAR(255) NOT NULL,
    address                  TEXT,
    phone                    VARCHAR(20),
    email                    VARCHAR(255),
    logo_path                VARCHAR(500),
    gstin                    VARCHAR(15),

    -- Business rules
    low_stock_threshold      INT,
    order_number_prefix      VARCHAR(10),
    default_tax_rate         DECIMAL(5,2),
    cgst_rate                DECIMAL(5,2),
    sgst_rate                DECIMAL(5,2),
    igst_rate                DECIMAL(5,2),

    -- Feature toggles
    enable_whatsapp_parsing  BIT(1),
    enable_storefront        BIT(1),

    -- Additional business rules
    default_shipping_charge  DECIMAL(10,2),
    minimum_order_value      DECIMAL(10,2),
    duplicate_check_days     INT,
    fuzzy_match_threshold    DECIMAL(3,2),

    -- Terms and conditions
    terms_and_conditions     TEXT,
    bank_details             TEXT,

    -- Audit fields
    last_updated_by          BIGINT,
    created_at               TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed a default configuration row only if the table is empty.
INSERT INTO company_config (
    company_name, address, phone, email, logo_path, gstin,
    low_stock_threshold, order_number_prefix,
    default_tax_rate, cgst_rate, sgst_rate, igst_rate,
    enable_whatsapp_parsing, enable_storefront,
    default_shipping_charge, minimum_order_value,
    duplicate_check_days, fuzzy_match_threshold
)
SELECT
    'Shifa Ayurveda', 'Not Configured', '0000000000', 'info@ayurveda.com', NULL, NULL,
    10, 'ORD',
    18.00, 9.00, 9.00, 18.00,
    b'1', b'1',
    0.00, 0.00,
    7, 0.60
WHERE NOT EXISTS (SELECT 1 FROM company_config);
