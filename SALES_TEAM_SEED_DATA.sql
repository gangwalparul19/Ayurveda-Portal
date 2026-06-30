-- ========================================
-- Sales Team Seed Data
-- Creates salespersons with targets and performance data
-- ========================================

USE shifa_db;

-- Step 1: Create platform users for salespersons and sales head
-- Password for all: "admin123" (same as admin user for consistency)
-- BCrypt hash: $2a$12$Xgu4cp5MFLQXRJgRTBVfouoIPsBqBqd2TmlhVlDe1A1rzSM0/1D3y

INSERT INTO platform_users (tenant_id, username, email, password_hash, role, full_name, is_active)
VALUES
-- Sales Head
(NULL, 'saleshead', 'saleshead@shifa.com', 
 '$2a$12$Xgu4cp5MFLQXRJgRTBVfouoIPsBqBqd2TmlhVlDe1A1rzSM0/1D3y', 
 'MANAGER', 'Rajesh Kumar (Sales Head)', b'1'),

-- Salesperson 1
(NULL, 'sp001', 'priya@shifa.com', 
 '$2a$12$Xgu4cp5MFLQXRJgRTBVfouoIPsBqBqd2TmlhVlDe1A1rzSM0/1D3y', 
 'SALESPERSON', 'Priya Sharma', b'1'),

-- Salesperson 2
(NULL, 'sp002', 'amit@shifa.com', 
 '$2a$12$Xgu4cp5MFLQXRJgRTBVfouoIPsBqBqd2TmlhVlDe1A1rzSM0/1D3y', 
 'SALESPERSON', 'Amit Patel', b'1'),

-- Salesperson 3
(NULL, 'sp003', 'neha@shifa.com', 
 '$2a$12$Xgu4cp5MFLQXRJgRTBVfouoIPsBqBqd2TmlhVlDe1A1rzSM0/1D3y', 
 'SALESPERSON', 'Neha Singh', b'1'),

-- Salesperson 4
(NULL, 'sp004', 'vikram@shifa.com', 
 '$2a$12$Xgu4cp5MFLQXRJgRTBVfouoIPsBqBqd2TmlhVlDe1A1rzSM0/1D3y', 
 'SALESPERSON', 'Vikram Reddy', b'1'),

-- Salesperson 5
(NULL, 'sp005', 'anita@shifa.com', 
 '$2a$12$Xgu4cp5MFLQXRJgRTBVfouoIPsBqBqd2TmlhVlDe1A1rzSM0/1D3y', 
 'SALESPERSON', 'Anita Desai', b'1');

-- Step 2: Create salesperson records in tenant database
-- First, we'll use variables to get the user IDs

SET @sp001_user_id = (SELECT id FROM platform_users WHERE username = 'sp001');
SET @sp002_user_id = (SELECT id FROM platform_users WHERE username = 'sp002');
SET @sp003_user_id = (SELECT id FROM platform_users WHERE username = 'sp003');
SET @sp004_user_id = (SELECT id FROM platform_users WHERE username = 'sp004');
SET @sp005_user_id = (SELECT id FROM platform_users WHERE username = 'sp005');

INSERT INTO salespersons (employee_code, name, phone, email, status, commission_rate, platform_user_id, joining_date)
VALUES
('SP001', 'Priya Sharma', '9876543210', 'priya@shifa.com', 'ACTIVE', 2.50, @sp001_user_id, '2024-01-15'),
('SP002', 'Amit Patel', '9876543211', 'amit@shifa.com', 'ACTIVE', 2.50, @sp002_user_id, '2024-01-15'),
('SP003', 'Neha Singh', '9876543212', 'neha@shifa.com', 'ACTIVE', 2.00, @sp003_user_id, '2024-03-01'),
('SP004', 'Vikram Reddy', '9876543213', 'vikram@shifa.com', 'ACTIVE', 2.00, @sp004_user_id, '2024-03-01'),
('SP005', 'Anita Desai', '9876543214', 'anita@shifa.com', 'ACTIVE', 1.50, @sp005_user_id, '2024-05-01');

-- Step 3: Set monthly targets for June 2026 (current month)
-- Three-tier target system: Tier 1 (Basic), Tier 2 (Mid), Tier 3 (Stretch)

SET @sp001_id = (SELECT id FROM salespersons WHERE employee_code = 'SP001');
SET @sp002_id = (SELECT id FROM salespersons WHERE employee_code = 'SP002');
SET @sp003_id = (SELECT id FROM salespersons WHERE employee_code = 'SP003');
SET @sp004_id = (SELECT id FROM salespersons WHERE employee_code = 'SP004');
SET @sp005_id = (SELECT id FROM salespersons WHERE employee_code = 'SP005');

INSERT INTO salesperson_targets (salesperson_user_id, month, year, target_amount, achieved_amount)
VALUES
-- Priya Sharma - Top performer
(@sp001_id, 6, 2026, 50000.00, 48500.00),
-- Amit Patel - Mid performer
(@sp002_id, 6, 2026, 50000.00, 42000.00),
-- Neha Singh - Consistent performer
(@sp003_id, 6, 2026, 45000.00, 38000.00),
-- Vikram Reddy - Stretch achiever
(@sp004_id, 6, 2026, 45000.00, 56000.00),
-- Anita Desai - New, building up
(@sp005_id, 6, 2026, 30000.00, 22000.00);

-- Step 4: Add previous month targets (May 2026)
INSERT INTO salesperson_targets (salesperson_user_id, month, year, target_amount, achieved_amount)
VALUES
(@sp001_id, 5, 2026, 50000.00, 52000.00),
(@sp002_id, 5, 2026, 50000.00, 39000.00),
(@sp003_id, 5, 2026, 45000.00, 46000.00),
(@sp004_id, 5, 2026, 45000.00, 61000.00),
(@sp005_id, 5, 2026, 30000.00, 28000.00);

-- Step 5: Create performance data for the current week (June 23-29, 2026)
-- Note: This table may not exist in cloud deployments - uncomment after creating the table
-- ALTER TABLE or CREATE TABLE statement needed first


INSERT INTO salesperson_performance (salesperson_id, performance_date, orders_count, total_sales, total_items_sold, commission_earned)
VALUES
-- June 23, 2026 (Monday)
(@sp001_id, '2026-06-23', 8, 12500.00, 45, 312.50),
(@sp002_id, '2026-06-23', 6, 9800.00, 32, 245.00),
(@sp003_id, '2026-06-23', 7, 8900.00, 38, 178.00),
(@sp004_id, '2026-06-23', 9, 13200.00, 48, 264.00),
(@sp005_id, '2026-06-23', 5, 6500.00, 25, 97.50),

-- June 24, 2026 (Tuesday)
(@sp001_id, '2026-06-24', 10, 14800.00, 52, 370.00),
(@sp002_id, '2026-06-24', 7, 11200.00, 39, 280.00),
(@sp003_id, '2026-06-24', 6, 7800.00, 31, 156.00),
(@sp004_id, '2026-06-24', 11, 15600.00, 56, 312.00),
(@sp005_id, '2026-06-24', 4, 5200.00, 18, 78.00),

-- June 25, 2026 (Wednesday)
(@sp001_id, '2026-06-25', 9, 13600.00, 48, 340.00),
(@sp002_id, '2026-06-25', 8, 10500.00, 41, 262.50),
(@sp003_id, '2026-06-25', 8, 9200.00, 42, 184.00),
(@sp004_id, '2026-06-25', 10, 14200.00, 51, 284.00),
(@sp005_id, '2026-06-25', 6, 7100.00, 28, 106.50),

-- June 26, 2026 (Thursday)
(@sp001_id, '2026-06-26', 7, 11200.00, 39, 280.00),
(@sp002_id, '2026-06-26', 5, 8600.00, 28, 215.00),
(@sp003_id, '2026-06-26', 9, 10800.00, 46, 216.00),
(@sp004_id, '2026-06-26', 8, 12400.00, 44, 248.00),
(@sp005_id, '2026-06-26', 7, 8200.00, 32, 123.00),

-- June 27, 2026 (Friday)
(@sp001_id, '2026-06-27', 11, 16200.00, 58, 405.00),
(@sp002_id, '2026-06-27', 9, 13400.00, 47, 335.00),
(@sp003_id, '2026-06-27', 7, 9600.00, 37, 192.00),
(@sp004_id, '2026-06-27', 12, 17800.00, 62, 356.00),
(@sp005_id, '2026-06-27', 6, 7400.00, 29, 111.00),

-- June 28, 2026 (Saturday - Weekend rush)
(@sp001_id, '2026-06-28', 13, 18900.00, 65, 472.50),
(@sp002_id, '2026-06-28', 11, 15800.00, 54, 395.00),
(@sp003_id, '2026-06-28', 10, 12400.00, 49, 248.00),
(@sp004_id, '2026-06-28', 14, 20600.00, 71, 412.00),
(@sp005_id, '2026-06-28', 8, 9800.00, 38, 147.00),

-- June 29, 2026 (Sunday - Weekend continued)
(@sp001_id, '2026-06-29', 12, 17400.00, 61, 435.00),
(@sp002_id, '2026-06-29', 10, 14200.00, 50, 355.00),
(@sp003_id, '2026-06-29', 9, 11100.00, 44, 222.00),
(@sp004_id, '2026-06-29', 13, 18900.00, 66, 378.00),
(@sp005_id, '2026-06-29', 7, 8600.00, 34, 129.00);

-- Step 6: Add historical performance data (last 3 weeks)
-- Week of June 16-22, 2026
INSERT INTO salesperson_performance (salesperson_id, performance_date, orders_count, total_sales, total_items_sold, commission_earned)
VALUES
-- Aggregated week data (one entry per day per person for simplicity)
(@sp001_id, '2026-06-16', 9, 13800.00, 48, 345.00),
(@sp002_id, '2026-06-16', 7, 10200.00, 36, 255.00),
(@sp003_id, '2026-06-16', 8, 9600.00, 40, 192.00),
(@sp004_id, '2026-06-16', 10, 14800.00, 52, 296.00),
(@sp005_id, '2026-06-16', 5, 6800.00, 27, 102.00);



-- Step 7: Create sample orders assigned to salespersons for testing order view
-- These are demo orders to test the masked view functionality

-- Note: Requires customers and products to exist (from SEED_DEMO_DATA)

-- Get customer IDs
SET @customer_1_id = (SELECT id FROM customers LIMIT 1);
SET @customer_2_id = (SELECT id FROM customers LIMIT 1 OFFSET 1);
SET @customer_3_id = (SELECT id FROM customers LIMIT 1 OFFSET 2);

-- Insert sample orders if customers exist
INSERT INTO orders (order_number, order_date, customer_id, salesperson_id, status, payment_status, payment_mode,
                    subtotal, tax_amount, shipping_charge, total_amount, order_source, notes)
SELECT 'ORD-SP001-20260627-001', '2026-06-27', @customer_1_id, @sp001_id, 'PAID', 'PAID', 'COD', 2500.00, 450.00, 50.00, 3000.00, 'MANUAL', 'Test order for sp001'
WHERE @customer_1_id IS NOT NULL;

INSERT INTO orders (order_number, order_date, customer_id, salesperson_id, status, payment_status, payment_mode,
                    subtotal, tax_amount, shipping_charge, total_amount, order_source, notes)
SELECT 'ORD-SP002-20260627-001', '2026-06-27', @customer_2_id, @sp002_id, 'CONFIRMED', 'PENDING', 'UPI', 1800.00, 324.00, 50.00, 2174.00, 'MANUAL', 'Test order for sp002'
WHERE @customer_2_id IS NOT NULL;

INSERT INTO orders (order_number, order_date, customer_id, salesperson_id, status, payment_status, payment_mode,
                    subtotal, tax_amount, shipping_charge, total_amount, order_source, notes)
SELECT 'ORD-SP001-20260626-001', '2026-06-26', @customer_3_id, @sp001_id, 'DELIVERED', 'PAID', 'COD', 3200.00, 576.00, 50.00, 3826.00, 'MANUAL', 'Delivered order for sp001'
WHERE @customer_3_id IS NOT NULL;

-- Generate correct BCrypt hash for password: admin123
-- Using the SAME hash as the admin user for consistency

-- Correct BCrypt hash for "admin123" (verified - same as admin user)
-- $2a$12$Xgu4cp5MFLQXRJgRTBVfouoIPsBqBqd2TmlhVlDe1A1rzSM0/1D3y

UPDATE platform_users 
SET password_hash = '$2a$12$Xgu4cp5MFLQXRJgRTBVfouoIPsBqBqd2TmlhVlDe1A1rzSM0/1D3y'
WHERE username IN ('saleshead', 'sp001', 'sp002', 'sp003', 'sp004', 'sp005');

-- Verify
SELECT username, full_name, role 
FROM platform_users 
WHERE username IN ('saleshead', 'sp001', 'sp002', 'sp003', 'sp004', 'sp005');

-- After running this, you can login with:
-- Username: sp001, sp002, sp003, sp004, sp005, saleshead
-- Password: admin123 (same as admin user)



-- Summary Statistics
SELECT 
    s.employee_code,
    s.name,
    s.commission_rate,
    COUNT(sp.id) as days_worked,
    SUM(sp.orders_count) as total_orders,
    SUM(sp.total_sales) as total_sales,
    SUM(sp.total_items_sold) as total_items,
    SUM(sp.commission_earned) as total_commission
FROM salespersons s
LEFT JOIN salesperson_performance sp ON s.id = sp.salesperson_id
WHERE sp.performance_date >= '2026-06-01'
GROUP BY s.id, s.employee_code, s.name, s.commission_rate
ORDER BY total_sales DESC;

-- Current Leaderboard (June 2026)
SELECT 
    ROW_NUMBER() OVER (ORDER BY SUM(sp.total_sales) DESC) as `rank`,
    s.employee_code,
    s.name,
    COUNT(sp.id) as days_worked,
    SUM(sp.orders_count) as total_orders,
    SUM(sp.total_sales) as total_sales,
    SUM(sp.total_items_sold) as total_items,
    CASE 
        WHEN ROW_NUMBER() OVER (ORDER BY SUM(sp.total_sales) DESC) = 1 THEN '🥇 Gold'
        WHEN ROW_NUMBER() OVER (ORDER BY SUM(sp.total_sales) DESC) = 2 THEN '🥈 Silver'
        WHEN ROW_NUMBER() OVER (ORDER BY SUM(sp.total_sales) DESC) = 3 THEN '🥉 Bronze'
        ELSE ''
    END as award
FROM salespersons s
LEFT JOIN salesperson_performance sp ON s.id = sp.salesperson_id
WHERE sp.performance_date >= '2026-06-01'
GROUP BY s.id, s.employee_code, s.name
ORDER BY total_sales DESC;

-- ========================================
-- Verification Queries
-- ========================================

-- Check all salesperson users
SELECT id, username, full_name, role FROM platform_users WHERE role = 'SALESPERSON';

-- Check salesperson records
SELECT * FROM salespersons;

-- Check targets
SELECT 
    st.*,
    s.name as salesperson_name,
    s.employee_code,
    CASE 
        WHEN st.tier_achieved = 3 THEN 'Tier 3 Achieved! 🏆'
        WHEN st.tier_achieved = 2 THEN 'Tier 2 Achieved! 🥈'
        WHEN st.tier_achieved = 1 THEN 'Tier 1 Achieved! 🥉'
        ELSE 'Below Target'
    END as status
FROM salesperson_targets st
JOIN salespersons s ON st.salesperson_user_id = s.id
WHERE st.month = 6 AND st.year = 2026;

-- Check performance data
SELECT 
    s.employee_code,
    s.name,
    sp.performance_date,
    sp.orders_count,
    sp.total_sales,
    sp.commission_earned
FROM salesperson_performance sp
JOIN salespersons s ON sp.salesperson_id = s.id
ORDER BY sp.performance_date DESC, sp.total_sales DESC
LIMIT 20;

-- ========================================
-- Login Credentials Summary
-- ========================================
/*
Sales Head:
Username: saleshead
Password: admin123
Role: MANAGER

Salespersons:
Username: sp001, sp002, sp003, sp004, sp005
Password: admin123 (same as admin user for consistency)
Role: SALESPERSON

Admin (existing):
Username: admin
Password: admin123
Role: TENANT_ADMIN
*/
