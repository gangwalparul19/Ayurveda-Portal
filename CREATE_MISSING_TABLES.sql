-- ========================================
-- Create Missing Tables for Cloud Database
-- ========================================
-- If you're getting errors about missing tables in your cloud deployment,
-- run this script to create them.

-- Use your tenant database
USE shifa_db;

-- ========================================
-- 1. Create salesperson_performance table
-- ========================================
-- This table stores daily performance metrics for each salesperson
-- Required for the dashboard to display performance data

CREATE TABLE IF NOT EXISTS salesperson_performance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    salesperson_id BIGINT NOT NULL,
    performance_date DATE NOT NULL,
    orders_count INT NOT NULL DEFAULT 0,
    total_sales DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_items_sold INT NOT NULL DEFAULT 0,
    commission_earned DECIMAL(10,2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_performance_salesperson (salesperson_id),
    INDEX idx_performance_date (performance_date),
    INDEX idx_performance_salesperson_date (salesperson_id, performance_date),
    
    CONSTRAINT fk_performance_salesperson
        FOREIGN KEY (salesperson_id) REFERENCES salespersons(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Verify table creation
-- ========================================
SELECT 'Checking tables...' as status;

SELECT TABLE_NAME 
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = 'shifa_db' 
AND TABLE_NAME IN ('salesperson_performance', 'salespersons', 'salesperson_targets')
ORDER BY TABLE_NAME;

-- ========================================
-- Summary & Next Steps
-- ========================================
-- 
-- After running this script:
-- 
-- 1. If you see 3 rows above (salesperson_performance, salesperson_targets, salespersons), 
--    then all tables exist. Continue to step 2.
--
-- 2. Run SALES_TEAM_SEED_DATA.sql to populate:
--    - Salesperson users and records
--    - Salesperson targets
--    - Sample orders
--    - (Performance data is commented out if table didn't exist)
--
-- 3. If performance data still shows as commented out in the seed file,
--    uncomment the performance data section and run it again.
--
-- 4. Login with:
--    Username: sp001, sp002, sp003, sp004, sp005
--    Password: admin123
--    Role: SALESPERSON
--
