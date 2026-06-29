-- V8: Product Engagement Columns
-- Adds whatsapp_share_count and view_count to products table
-- Uses a stored procedure to guard against re-running (idempotent)

DROP PROCEDURE IF EXISTS add_engagement_columns;

DELIMITER $$
CREATE PROCEDURE add_engagement_columns()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'products'
          AND COLUMN_NAME  = 'whatsapp_share_count'
    ) THEN
        ALTER TABLE products ADD COLUMN whatsapp_share_count INT DEFAULT 0;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'products'
          AND COLUMN_NAME  = 'view_count'
    ) THEN
        ALTER TABLE products ADD COLUMN view_count INT DEFAULT 0;
    END IF;
END$$
DELIMITER ;

CALL add_engagement_columns();
DROP PROCEDURE IF EXISTS add_engagement_columns;
