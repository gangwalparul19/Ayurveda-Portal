-- =====================================================
-- V2: Performance optimization indexes (Task 31.1)
-- Adds composite indexes for common query patterns and ensures
-- indexes exist on frequently-queried columns for tables that may
-- have been created outside the V1 template (payment_records,
-- stock_history, salespersons).
--
-- MySQL does not support "CREATE INDEX IF NOT EXISTS", so a helper
-- procedure is used to add each index only when it does not already
-- exist. This keeps the migration idempotent and safe to re-run.
-- =====================================================

DELIMITER //

DROP PROCEDURE IF EXISTS add_index_if_missing //
CREATE PROCEDURE add_index_if_missing(
    IN p_table   VARCHAR(128),
    IN p_index   VARCHAR(128),
    IN p_columns VARCHAR(255),
    IN p_unique  BOOLEAN
)
BEGIN
    -- Only attempt if the target table exists in the current schema
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table
    ) THEN
        -- Only create the index when it is not already present
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = p_table
              AND INDEX_NAME = p_index
        ) THEN
            SET @ddl = CONCAT(
                'CREATE ', IF(p_unique, 'UNIQUE ', ''), 'INDEX ',
                p_index, ' ON ', p_table, ' (', p_columns, ')'
            );
            PREPARE stmt FROM @ddl;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END IF;
END //

DELIMITER ;

-- ----- ORDERS: composite indexes for common query patterns -----
CALL add_index_if_missing('orders', 'idx_orders_status_date',       'status, order_date',         FALSE);
CALL add_index_if_missing('orders', 'idx_orders_customer_date',     'customer_id, order_date',     FALSE);
CALL add_index_if_missing('orders', 'idx_orders_salesperson_date',  'salesperson_id, order_date',  FALSE);

-- ----- ORDER ITEMS (covered by V1, ensured here for completeness) -----
CALL add_index_if_missing('order_items', 'idx_items_order',   'order_id',   FALSE);
CALL add_index_if_missing('order_items', 'idx_items_product', 'product_id', FALSE);

-- ----- PAYMENT RECORDS (table may not exist in V1 template) -----
CALL add_index_if_missing('payment_records', 'idx_payments_order', 'order_id',     FALSE);
CALL add_index_if_missing('payment_records', 'idx_payments_date',  'payment_date', FALSE);

-- ----- STOCK HISTORY (table may not exist in V1 template) -----
CALL add_index_if_missing('stock_history', 'idx_stock_product',   'product_id', FALSE);
CALL add_index_if_missing('stock_history', 'idx_stock_created',   'created_at', FALSE);
CALL add_index_if_missing('stock_history', 'idx_stock_operation', 'operation',  FALSE);

-- ----- SALESPERSONS (table may not exist in V1 template) -----
CALL add_index_if_missing('salespersons', 'idx_salespersons_code',   'employee_code',    TRUE);
CALL add_index_if_missing('salespersons', 'idx_salespersons_status', 'status',           FALSE);
CALL add_index_if_missing('salespersons', 'idx_salespersons_user',   'platform_user_id', FALSE);

DROP PROCEDURE IF EXISTS add_index_if_missing;
