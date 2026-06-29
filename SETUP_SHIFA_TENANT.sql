-- =====================================================
-- COMPLETE SHIFA TENANT SETUP
-- Run this entire script to set up the Shifa storefront
-- =====================================================

-- Step 1: Create Shifa tenant database
CREATE DATABASE IF NOT EXISTS shifa_db 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

-- Step 2: Add Shifa tenant to master database
USE ayurveda_master;

-- Check if Shifa already exists (will show error if exists, that's OK)
INSERT INTO tenants 
(tenant_key, company_name, db_url, db_username, db_password, status, subscription_plan, contact_email, contact_phone)
VALUES
('shifa', 'Shifa Ayurveda', 'jdbc:mysql://localhost:3306/shifa_db', 'root', 'root@123', 'ACTIVE', 'PREMIUM', 'admin@shifa.com', '+91 9876543210')
ON DUPLICATE KEY UPDATE 
    status = 'ACTIVE',
    db_url = 'jdbc:mysql://localhost:3306/shifa_db',
    db_username = 'root',
    db_password = 'root@123';

-- Get the tenant ID
SET @shifa_tenant_id = (SELECT id FROM tenants WHERE tenant_key = 'shifa');

-- Step 3: Add UI config with storefront enabled
INSERT INTO tenant_ui_config
(tenant_id, primary_color, secondary_color, accent_color, logo_url, storefront_enabled)
VALUES
(@shifa_tenant_id, '#2E7D32', '#FF6F00', '#1976D2', '/assets/images/shifa/logo.png', TRUE)
ON DUPLICATE KEY UPDATE
    storefront_enabled = TRUE,
    primary_color = '#2E7D32',
    secondary_color = '#FF6F00',
    accent_color = '#1976D2';

-- Step 4: Create tables in Shifa database
USE shifa_db;

-- ----- PRODUCTS -----
CREATE TABLE IF NOT EXISTS products (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku                 VARCHAR(50) NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    category            VARCHAR(100),
    mrp                 DECIMAL(10,2) NOT NULL,
    sale_price          DECIMAL(10,2) NOT NULL,
    unit                VARCHAR(20) DEFAULT 'pcs',
    weight_grams        DECIMAL(10,2),
    hsn_code            VARCHAR(20),
    gst_rate            DECIMAL(4,2) DEFAULT 0.00,
    image_url           VARCHAR(500),
    is_active           BOOLEAN DEFAULT TRUE,
    stock_quantity      INT DEFAULT 0,
    low_stock_threshold INT DEFAULT 10,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_products_category (category),
    INDEX idx_products_active (is_active),
    INDEX idx_products_sku (sku)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----- CUSTOMERS -----
CREATE TABLE IF NOT EXISTS customers (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    phone           VARCHAR(20) UNIQUE,
    email           VARCHAR(255),
    address_line_1  VARCHAR(500),
    address_line_2  VARCHAR(500),
    city            VARCHAR(100),
    state           VARCHAR(100),
    pincode         VARCHAR(10),
    gstin           VARCHAR(20),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_customers_phone (phone),
    INDEX idx_customers_name (name),
    INDEX idx_customers_city (city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----- ORDERS -----
CREATE TABLE IF NOT EXISTS orders (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number        VARCHAR(30) NOT NULL UNIQUE,
    customer_id         BIGINT,
    salesperson_id      BIGINT,
    order_source        ENUM('WHATSAPP', 'MANUAL', 'STOREFRONT', 'API') NOT NULL DEFAULT 'MANUAL',
    raw_whatsapp_text   TEXT,
    status              ENUM('NEW', 'CONFIRMED', 'PAID', 'PACKED', 'DISPATCHED', 'DELIVERED', 'CANCELLED', 'RETURNED')
                        NOT NULL DEFAULT 'NEW',
    subtotal            DECIMAL(12,2) DEFAULT 0.00,
    discount_amount     DECIMAL(10,2) DEFAULT 0.00,
    tax_amount          DECIMAL(10,2) DEFAULT 0.00,
    shipping_charge     DECIMAL(10,2) DEFAULT 0.00,
    total_amount        DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    payment_mode        ENUM('COD', 'UPI', 'BANK_TRANSFER', 'ONLINE', 'CREDIT'),
    payment_status      ENUM('PENDING', 'PARTIAL', 'PAID', 'REFUNDED') DEFAULT 'PENDING',
    notes               TEXT,
    order_date          DATE NOT NULL,
    dispatched_at       TIMESTAMP NULL,
    delivered_at        TIMESTAMP NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_orders_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id)
        ON DELETE SET NULL,

    INDEX idx_orders_status (status),
    INDEX idx_orders_date (order_date),
    INDEX idx_orders_customer (customer_id),
    INDEX idx_orders_salesperson (salesperson_id),
    INDEX idx_orders_source (order_source),
    INDEX idx_orders_payment (payment_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----- ORDER ITEMS -----
CREATE TABLE IF NOT EXISTS order_items (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id                BIGINT NOT NULL,
    product_id              BIGINT,
    product_name_snapshot   VARCHAR(255) NOT NULL,
    sku_snapshot            VARCHAR(50),
    quantity                INT NOT NULL,
    unit_price              DECIMAL(10,2) NOT NULL,
    mrp_snapshot            DECIMAL(10,2),
    discount                DECIMAL(10,2) DEFAULT 0.00,
    tax_amount              DECIMAL(10,2) DEFAULT 0.00,
    line_total              DECIMAL(12,2) NOT NULL,

    CONSTRAINT fk_items_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_items_product
        FOREIGN KEY (product_id) REFERENCES products(id)
        ON DELETE SET NULL,

    INDEX idx_items_order (order_id),
    INDEX idx_items_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----- ORDER STATUS HISTORY -----
CREATE TABLE IF NOT EXISTS order_status_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT NOT NULL,
    from_status     VARCHAR(30),
    to_status       VARCHAR(30) NOT NULL,
    changed_by      BIGINT,
    notes           TEXT,
    changed_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_history_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON DELETE CASCADE,

    INDEX idx_history_order (order_id),
    INDEX idx_history_date (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----- DISPATCH LABELS -----
CREATE TABLE IF NOT EXISTS dispatch_labels (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT NOT NULL,
    batch_id        VARCHAR(50),
    courier_partner VARCHAR(100),
    tracking_number VARCHAR(100),
    label_pdf_path  VARCHAR(500),
    weight_grams    DECIMAL(10,2),
    dimensions      VARCHAR(50),
    status          ENUM('GENERATED', 'PRINTED', 'SHIPPED', 'DELIVERED') DEFAULT 'GENERATED',
    generated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_labels_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON DELETE CASCADE,

    INDEX idx_labels_order (order_id),
    INDEX idx_labels_batch (batch_id),
    INDEX idx_labels_tracking (tracking_number),
    INDEX idx_labels_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----- BILLING EXPORTS -----
CREATE TABLE IF NOT EXISTS billing_exports (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    export_type         ENUM('VYAPAR_CSV', 'VYAPAR_EXCEL', 'GST_JSON', 'CUSTOM') NOT NULL,
    date_range_start    DATE,
    date_range_end      DATE,
    file_path           VARCHAR(500),
    record_count        INT DEFAULT 0,
    generated_by        BIGINT,
    generated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_exports_type (export_type),
    INDEX idx_exports_date (generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----- SALESPERSON TARGETS -----
CREATE TABLE IF NOT EXISTS salesperson_targets (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    salesperson_user_id     BIGINT NOT NULL,
    month                   INT NOT NULL,
    year                    INT NOT NULL,
    target_amount           DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    achieved_amount         DECIMAL(12,2) DEFAULT 0.00,

    UNIQUE KEY uk_target (salesperson_user_id, month, year),
    INDEX idx_targets_salesperson (salesperson_user_id),
    INDEX idx_targets_period (year, month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Step 5: Add sample products
INSERT INTO products 
(sku, name, description, category, mrp, sale_price, unit, weight_grams, stock_quantity, is_active, image_url)
VALUES
('SHIFA-001', 'Ashwagandha Capsules', 'Premium quality Ashwagandha for stress relief and energy boost. Each bottle contains 60 capsules of 500mg each.', 'Capsules', 599.00, 499.00, 'bottle', 100, 50, TRUE, '/assets/images/shifa/products/1.jpg'),
('SHIFA-002', 'Triphala Powder', 'Natural digestive support and detoxification. Pure blend of Amalaki, Bibhitaki and Haritaki.', 'Powder', 299.00, 249.00, 'pack', 200, 100, TRUE, '/assets/images/shifa/products/2.jpg'),
('SHIFA-003', 'Chyawanprash', 'Immunity booster with 41 natural ingredients. Traditional Ayurvedic recipe for overall wellness.', 'Paste', 450.00, 399.00, 'jar', 500, 75, TRUE, '/assets/images/shifa/products/3.jpg'),
('SHIFA-004', 'Giloy Juice', 'Pure Giloy (Guduchi) extract for immunity and fever management. 100% natural with no added sugar.', 'Juice', 350.00, 299.00, 'bottle', 500, 60, TRUE, '/assets/images/shifa/products/4.jpg'),
('SHIFA-005', 'Tulsi Drops', 'Holy Basil drops for respiratory health and immunity. Rich in antioxidants and essential oils.', 'Drops', 199.00, 169.00, 'bottle', 30, 120, TRUE, '/assets/images/shifa/products/5.jpg'),
('SHIFA-006', 'Amla Candy', 'Vitamin C rich Indian Gooseberry candy. Natural immunity booster and digestive aid.', 'Candy', 150.00, 129.00, 'pack', 200, 200, TRUE, '/assets/images/shifa/products/6.jpg'),
('SHIFA-007', 'Brahmi Syrup', 'Brain tonic for memory and concentration. Helps reduce stress and improve mental clarity.', 'Syrup', 275.00, 249.00, 'bottle', 200, 80, TRUE, '/assets/images/shifa/products/7.jpg'),
('SHIFA-008', 'Neem Capsules', 'Blood purifier and skin health support. Natural antibacterial and antifungal properties.', 'Capsules', 399.00, 349.00, 'bottle', 60, 90, TRUE, '/assets/images/shifa/products/8.jpg'),
('SHIFA-009', 'Karela Jamun Juice', 'Natural diabetes management support. Helps regulate blood sugar levels naturally.', 'Juice', 425.00, 375.00, 'bottle', 500, 45, TRUE, '/assets/images/shifa/products/9.jpg'),
('SHIFA-010', 'Aloe Vera Juice', 'Digestive health and skin care. Pure Aloe extract with natural pulp for better absorption.', 'Juice', 325.00, 289.00, 'bottle', 500, 100, TRUE, '/assets/images/shifa/products/10.jpg'),
('SHIFA-011', 'Shilajit Resin', 'Premium Himalayan Shilajit for vitality and energy. Rich in fulvic acid and 85+ minerals.', 'Resin', 999.00, 849.00, 'jar', 20, 30, TRUE, '/assets/images/shifa/products/11.jpg'),
('SHIFA-012', 'Moringa Powder', 'Nutrient-rich superfood powder. Contains 92 nutrients and 46 antioxidants.', 'Powder', 375.00, 329.00, 'pack', 200, 85, TRUE, '/assets/images/shifa/products/12.jpg'),
('SHIFA-013', 'Arjuna Tea', 'Heart health support tea. Natural cardio-tonic with Terminalia Arjuna bark.', 'Tea', 225.00, 199.00, 'pack', 100, 110, TRUE, '/assets/images/shifa/products/13.jpg'),
('SHIFA-014', 'Kumkumadi Oil', 'Skin brightening facial oil. Traditional blend of 16 herbs and saffron for radiant skin.', 'Oil', 899.00, 799.00, 'bottle', 50, 40, TRUE, '/assets/images/shifa/products/14.jpg'),
('SHIFA-015', 'Trikatu Churna', 'Digestive fire booster. Blend of ginger, black pepper and long pepper for metabolism.', 'Powder', 189.00, 159.00, 'pack', 100, 150, TRUE, '/assets/images/shifa/products/15.jpg'),
('SHIFA-016', 'Shatavari Capsules', 'Women wellness supplement. Supports hormonal balance and reproductive health.', 'Capsules', 549.00, 479.00, 'bottle', 80, 65, TRUE, '/assets/images/shifa/products/16.jpg'),
('SHIFA-017', 'Haridra Capsules', 'Turmeric capsules with 95% curcumin. Anti-inflammatory and joint health support.', 'Capsules', 449.00, 399.00, 'bottle', 70, 95, TRUE, '/assets/images/shifa/products/17.jpg'),
('SHIFA-018', 'Haritaki Powder', 'King of medicines powder. Supports digestion, detoxification and rejuvenation.', 'Powder', 249.00, 219.00, 'pack', 150, 125, TRUE, '/assets/images/shifa/products/18.jpg')
ON DUPLICATE KEY UPDATE 
    name = VALUES(name),
    description = VALUES(description),
    stock_quantity = VALUES(stock_quantity);

-- Step 6: Verify setup
SELECT 'SETUP COMPLETE!' as Status;
SELECT CONCAT('Tenant ID: ', @shifa_tenant_id) as Info;
SELECT CONCAT('Products added: ', COUNT(*)) as Products FROM products;
SELECT CONCAT('Storefront enabled: ', storefront_enabled) as Storefront 
FROM ayurveda_master.tenant_ui_config 
WHERE tenant_id = @shifa_tenant_id;

-- =====================================================
-- NEXT STEPS:
-- 1. Restart your Spring Boot backend
-- 2. Open http://localhost:4200/store
-- 3. You should see the Shifa storefront with 18 products!
-- =====================================================
