-- V9: Coupons, Coupon Usage Tracking, and Payment / Coupon columns on orders
-- All statements are idempotent (IF NOT EXISTS / stored-procedure guard).

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Coupons master table
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS coupons (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(30) NOT NULL UNIQUE,
    description         VARCHAR(255),
    discount_type       ENUM('PERCENT', 'FLAT') NOT NULL DEFAULT 'PERCENT',
    discount_value      DECIMAL(10,2) NOT NULL,
    min_order_amount    DECIMAL(10,2) DEFAULT 0.00,
    max_discount_amount DECIMAL(10,2) DEFAULT NULL,
    usage_limit         INT DEFAULT NULL,
    usage_count         INT DEFAULT 0,
    per_user_limit      INT DEFAULT 1,
    is_active           BIT(1) DEFAULT b'1',
    valid_from          DATE DEFAULT (CURDATE()),
    valid_until         DATE DEFAULT NULL,
    applicable_category VARCHAR(100) DEFAULT NULL,
    created_by          BIGINT DEFAULT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_coupon_code   (code),
    INDEX idx_coupon_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Coupon usage tracking table
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS coupon_usages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_id       BIGINT NOT NULL,
    order_id        BIGINT DEFAULT NULL,
    customer_phone  VARCHAR(20),
    used_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cu_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE CASCADE,
    INDEX idx_cu_coupon (coupon_id),
    INDEX idx_cu_phone  (customer_phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Add coupon + Razorpay columns to orders (idempotent via stored procedure)
-- ─────────────────────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS add_orders_payment_coupon_columns;

DELIMITER $$
CREATE PROCEDURE add_orders_payment_coupon_columns()
BEGIN
    -- coupon_id
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'orders'
          AND COLUMN_NAME  = 'coupon_id'
    ) THEN
        ALTER TABLE orders ADD COLUMN coupon_id BIGINT DEFAULT NULL;
    END IF;

    -- coupon_discount
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'orders'
          AND COLUMN_NAME  = 'coupon_discount'
    ) THEN
        ALTER TABLE orders ADD COLUMN coupon_discount DECIMAL(10,2) DEFAULT 0.00;
    END IF;

    -- razorpay_order_id
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'orders'
          AND COLUMN_NAME  = 'razorpay_order_id'
    ) THEN
        ALTER TABLE orders ADD COLUMN razorpay_order_id VARCHAR(50) DEFAULT NULL;
    END IF;

    -- razorpay_payment_id
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'orders'
          AND COLUMN_NAME  = 'razorpay_payment_id'
    ) THEN
        ALTER TABLE orders ADD COLUMN razorpay_payment_id VARCHAR(50) DEFAULT NULL;
    END IF;
END$$
DELIMITER ;

CALL add_orders_payment_coupon_columns();
DROP PROCEDURE IF EXISTS add_orders_payment_coupon_columns;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Seed sample coupons (INSERT IGNORE keeps it idempotent on re-run)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT IGNORE INTO coupons
    (code, description, discount_type, discount_value, min_order_amount, max_discount_amount, usage_limit, is_active, valid_from, valid_until)
VALUES
    ('WELCOME10', 'Welcome discount',              'PERCENT', 10.00, 300.00, 100.00, 100, b'1', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 YEAR)),
    ('FLAT50',    '₹50 flat off',                  'FLAT',    50.00, 500.00,   NULL, 200, b'1', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 YEAR)),
    ('SHIFA20',   '20% off on orders above ₹800',  'PERCENT', 20.00, 800.00, 200.00,  50, b'1', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 YEAR));
