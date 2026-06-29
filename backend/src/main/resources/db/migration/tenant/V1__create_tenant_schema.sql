-- =====================================================
-- Tenant Database Schema Template
-- This script is executed when a new tenant database is provisioned.
-- Each tenant gets their own dedicated MySQL database with this schema.
-- =====================================================

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
