-- V7: Storefront Users and Product Reviews
-- Creates tables for storefront customer accounts and product review functionality

CREATE TABLE IF NOT EXISTS storefront_users (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id         BIGINT UNIQUE,
    email               VARCHAR(255) NOT NULL UNIQUE,
    phone               VARCHAR(20),
    password_hash       VARCHAR(255) NOT NULL,
    full_name           VARCHAR(255),
    is_verified         BIT(1) DEFAULT b'0',
    is_active           BIT(1) DEFAULT b'1',
    last_login_at       DATETIME(6),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sf_users_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_reviews (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id           BIGINT NOT NULL,
    customer_id          BIGINT,
    storefront_user_id   BIGINT,
    reviewer_name        VARCHAR(255) NOT NULL,
    rating               TINYINT NOT NULL,
    title                VARCHAR(255),
    review_text          TEXT,
    is_verified_purchase BIT(1) DEFAULT b'0',
    is_approved          BIT(1) DEFAULT b'0',
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reviews_product   FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_customer  FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL,
    CONSTRAINT fk_reviews_sf_user   FOREIGN KEY (storefront_user_id) REFERENCES storefront_users(id) ON DELETE SET NULL,
    INDEX idx_reviews_product (product_id),
    INDEX idx_reviews_approved (is_approved),
    CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
