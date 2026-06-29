-- =====================================================================
-- SEED_DEMO_DATA.sql
-- Demo seed dataset for the Ayurveda Order & Dispatch Management System
-- Target DB: shifa_db  (MySQL 8.0)
--
-- Safe to re-run: every row this script inserts is tagged with a demo
-- marker and removed at the top before re-inserting. It NEVER touches
-- the real SHIFA products, company_config, or platform_users.
--
-- NOTE: This script does NOT create any products. It reuses the real
-- catalogue products SHIFA-001..SHIFA-018 (looked up by SKU) for every
-- order line item, so all demo orders reference real, image-backed
-- products with correct prices.
--
-- Demo markers:
--   salespersons  : employee_code LIKE 'DEMO-%'
--   customers     : email LIKE '%@shifademo.test'
--   orders        : notes LIKE '%[DEMOSEED]%'
--   products      : sku LIKE 'DEMO-%'  (legacy demo products are purged)
-- =====================================================================

USE shifa_db;

-- ---------------------------------------------------------------------
-- 0. CLEANUP previous demo data (child rows first for referential safety)
-- ---------------------------------------------------------------------
DELETE FROM order_status_history
 WHERE order_id IN (SELECT id FROM orders WHERE notes LIKE '%[DEMOSEED]%');

DELETE FROM payment_records
 WHERE order_id IN (SELECT id FROM orders WHERE notes LIKE '%[DEMOSEED]%');

DELETE FROM order_items
 WHERE order_id IN (SELECT id FROM orders WHERE notes LIKE '%[DEMOSEED]%');

DELETE FROM orders WHERE notes LIKE '%[DEMOSEED]%';

-- Purge legacy image-less DEMO products (and their stock history) if present.
-- This NEVER touches the real SHIFA-001..SHIFA-018 catalogue products.
DELETE FROM stock_history
 WHERE product_id IN (SELECT id FROM products WHERE sku LIKE 'DEMO-%');

DELETE FROM products    WHERE sku LIKE 'DEMO-%';
DELETE FROM customers   WHERE email LIKE '%@shifademo.test';
DELETE FROM salespersons WHERE employee_code LIKE 'DEMO-%';

-- ---------------------------------------------------------------------
-- Date anchors (computed so the data is always "current" when run)
--   @d_today  = today
--   @d_m1..m4 = within the current month, never in the future
--   @d_prev   = roughly previous month (for historical variety)
-- ---------------------------------------------------------------------
SET @month_start = DATE_FORMAT(CURDATE(), '%Y-%m-01');
SET @d_today = CURDATE();
SET @d_m1 = LEAST(DATE_ADD(@month_start, INTERVAL 2  DAY), CURDATE());
SET @d_m2 = LEAST(DATE_ADD(@month_start, INTERVAL 6  DAY), CURDATE());
SET @d_m3 = LEAST(DATE_ADD(@month_start, INTERVAL 9  DAY), CURDATE());
SET @d_m4 = LEAST(DATE_ADD(@month_start, INTERVAL 13 DAY), CURDATE());
SET @d_prev = DATE_SUB(CURDATE(), INTERVAL 40 DAY);

-- ---------------------------------------------------------------------
-- 1. SALESPERSONS (platform_user_id -> admin id 1, NOT NULL)
-- ---------------------------------------------------------------------
INSERT INTO salespersons
    (employee_code, name, phone, email, status, commission_rate, platform_user_id, joining_date, created_at, updated_at)
VALUES
    ('DEMO-S01', 'Rahul Desai',     '9000000001', 'rahul@shifademo.test',  'ACTIVE', 5.00, 1, DATE_SUB(CURDATE(), INTERVAL 400 DAY), NOW(), NOW()),
    ('DEMO-S02', 'Anita Kulkarni',  '9000000002', 'anita@shifademo.test',  'ACTIVE', 7.50, 1, DATE_SUB(CURDATE(), INTERVAL 300 DAY), NOW(), NOW()),
    ('DEMO-S03', 'Vikram Singh',    '9000000003', 'vikram@shifademo.test', 'ACTIVE', 6.00, 1, DATE_SUB(CURDATE(), INTERVAL 200 DAY), NOW(), NOW());

-- ---------------------------------------------------------------------
-- 2. CUSTOMERS (8, across multiple states/cities)
-- ---------------------------------------------------------------------
INSERT INTO customers
    (name, phone, email, address_line_1, address_line_2, city, state, pincode, gstin, created_at, updated_at)
VALUES
    ('Ravi Sharma',   '9820012301', 'ravi@shifademo.test',    '12 Marine Drive',        'Near Charni Road',   'Mumbai',    'Maharashtra', '400001', NULL, NOW(), NOW()),
    ('Priya Nair',    '9845012302', 'priya@shifademo.test',   '45 MG Road',             'Brigade Towers',     'Bengaluru', 'Karnataka',   '560001', NULL, NOW(), NOW()),
    ('Amit Verma',    '9811012303', 'amit@shifademo.test',    '8 Connaught Place',      'Block A',            'New Delhi', 'Delhi',       '110001', NULL, NOW(), NOW()),
    ('Lakshmi Iyer',  '9840012304', 'lakshmi@shifademo.test', '23 Anna Salai',          'T Nagar',            'Chennai',   'Tamil Nadu',  '600001', NULL, NOW(), NOW()),
    ('Kiran Patel',   '9824012305', 'kiran@shifademo.test',   '56 CG Road',             'Navrangpura',        'Ahmedabad', 'Gujarat',     '380001', NULL, NOW(), NOW()),
    ('Sunita Joshi',  '9821012306', 'sunita@shifademo.test',  '9 FC Road',              'Shivajinagar',       'Pune',      'Maharashtra', '411001', NULL, NOW(), NOW()),
    ('Manoj Reddy',   '9849012307', 'manoj@shifademo.test',   '101 Indiranagar',        '100ft Road',         'Bengaluru', 'Karnataka',   '560002', NULL, NOW(), NOW()),
    ('Deepa Menon',   '9847012308', 'deepa@shifademo.test',   '34 Adyar Main Road',     'Besant Nagar',       'Chennai',   'Tamil Nadu',  '600002', NULL, NOW(), NOW());

-- ---------------------------------------------------------------------
-- 3. ORDERS + ITEMS + PAYMENTS + STATUS HISTORY
--    Every line item references a REAL product (SHIFA-001..SHIFA-018),
--    looked up by SKU. Snapshots use the real name/sku/mrp; unit_price
--    uses the real sale_price.
--    Math rule: line_total = qty*unit_price - discount + tax_amount
--               subtotal   = SUM(line_total)
--               total_amount = subtotal - discount_amount + tax_amount + shipping_charge
--    Every order tagged with '[DEMOSEED]' in notes.
-- ---------------------------------------------------------------------

-- ===== O1  DELIVERED  (this month)  c1 / S01 ==========================
-- items: SHIFA-001 x2 (998) + SHIFA-005 x1 (169) = 1167 ; +5% tax 58.35 ; ship 50
INSERT INTO orders (order_number, customer_id, salesperson_id, order_source, status,
    subtotal, discount_amount, tax_amount, shipping_charge, total_amount,
    payment_mode, payment_status, notes, order_date, dispatched_at, delivered_at)
VALUES (CONCAT('ORD-', DATE_FORMAT(@d_m1,'%Y%m%d'), '-0001'),
    (SELECT id FROM customers WHERE email='ravi@shifademo.test'),
    (SELECT id FROM salespersons WHERE employee_code='DEMO-S01'),
    'MANUAL', 'DELIVERED', 1167.00, 0.00, 58.35, 50.00, 1275.35,
    'UPI', 'PAID', '[DEMOSEED] Delivered this month', @d_m1,
    TIMESTAMP(@d_m1,'14:00:00'), TIMESTAMP(DATE_ADD(@d_m1, INTERVAL 2 DAY),'11:00:00'));
SET @o1 = LAST_INSERT_ID();
INSERT INTO order_items (order_id, product_id, product_name_snapshot, sku_snapshot, quantity, unit_price, mrp_snapshot, discount, tax_amount, line_total) VALUES
    (@o1, (SELECT id FROM products WHERE sku='SHIFA-001'), 'Ashwagandha Capsules', 'SHIFA-001', 2, 499.00, 599.00, 0.00, 0.00, 998.00),
    (@o1, (SELECT id FROM products WHERE sku='SHIFA-005'), 'Tulsi Drops',          'SHIFA-005', 1, 169.00, 199.00, 0.00, 0.00, 169.00);
INSERT INTO payment_records (amount, payment_mode, transaction_reference, payment_date, notes, recorded_by, order_id)
VALUES (1275.35, 'UPI', 'TXN-DEMO-0001', TIMESTAMP(@d_m1,'14:05:00'), '[DEMOSEED]', 1, @o1);
INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, notes, changed_at) VALUES
    (@o1, NULL, 'NEW', 1, '[DEMOSEED] created', TIMESTAMP(@d_m1,'10:00:00')),
    (@o1, 'NEW', 'DELIVERED', 1, '[DEMOSEED] fulfilled', TIMESTAMP(DATE_ADD(@d_m1, INTERVAL 2 DAY),'11:00:00'));

-- ===== O2  DELIVERED  (this month)  c2 / S02 ==========================
-- items: SHIFA-003 x1 (399) + SHIFA-006 x2 (258) = 657 ; disc 50 ; tax 32.85 ; ship 50
INSERT INTO orders (order_number, customer_id, salesperson_id, order_source, status,
    subtotal, discount_amount, tax_amount, shipping_charge, total_amount,
    payment_mode, payment_status, notes, order_date, dispatched_at, delivered_at)
VALUES (CONCAT('ORD-', DATE_FORMAT(@d_m2,'%Y%m%d'), '-0002'),
    (SELECT id FROM customers WHERE email='priya@shifademo.test'),
    (SELECT id FROM salespersons WHERE employee_code='DEMO-S02'),
    'WHATSAPP', 'DELIVERED', 657.00, 50.00, 32.85, 50.00, 689.85,
    'COD', 'PAID', '[DEMOSEED] Delivered this month', @d_m2,
    TIMESTAMP(@d_m2,'15:00:00'), TIMESTAMP(DATE_ADD(@d_m2, INTERVAL 2 DAY),'12:00:00'));
SET @o2 = LAST_INSERT_ID();
INSERT INTO order_items (order_id, product_id, product_name_snapshot, sku_snapshot, quantity, unit_price, mrp_snapshot, discount, tax_amount, line_total) VALUES
    (@o2, (SELECT id FROM products WHERE sku='SHIFA-003'), 'Chyawanprash', 'SHIFA-003', 1, 399.00, 450.00, 0.00, 0.00, 399.00),
    (@o2, (SELECT id FROM products WHERE sku='SHIFA-006'), 'Amla Candy',   'SHIFA-006', 2, 129.00, 150.00, 0.00, 0.00, 258.00);
INSERT INTO payment_records (amount, payment_mode, transaction_reference, payment_date, notes, recorded_by, order_id)
VALUES (689.85, 'COD', 'TXN-DEMO-0002', TIMESTAMP(DATE_ADD(@d_m2, INTERVAL 2 DAY),'12:00:00'), '[DEMOSEED]', 1, @o2);
INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, notes, changed_at) VALUES
    (@o2, NULL, 'NEW', 1, '[DEMOSEED] created', TIMESTAMP(@d_m2,'09:30:00')),
    (@o2, 'NEW', 'DELIVERED', 1, '[DEMOSEED] fulfilled', TIMESTAMP(DATE_ADD(@d_m2, INTERVAL 2 DAY),'12:00:00'));

-- ===== O3  DELIVERED  (this month)  c3 / S03 ==========================
-- items: SHIFA-002 x3 (747) + SHIFA-008 x1 (349) = 1096 ; tax 54.80 ; ship 0
INSERT INTO orders (order_number, customer_id, salesperson_id, order_source, status,
    subtotal, discount_amount, tax_amount, shipping_charge, total_amount,
    payment_mode, payment_status, notes, order_date, dispatched_at, delivered_at)
VALUES (CONCAT('ORD-', DATE_FORMAT(@d_m3,'%Y%m%d'), '-0003'),
    (SELECT id FROM customers WHERE email='amit@shifademo.test'),
    (SELECT id FROM salespersons WHERE employee_code='DEMO-S03'),
    'STOREFRONT', 'DELIVERED', 1096.00, 0.00, 54.80, 0.00, 1150.80,
    'ONLINE', 'PAID', '[DEMOSEED] Delivered this month', @d_m3,
    TIMESTAMP(@d_m3,'16:00:00'), TIMESTAMP(DATE_ADD(@d_m3, INTERVAL 3 DAY),'13:00:00'));
SET @o3 = LAST_INSERT_ID();
INSERT INTO order_items (order_id, product_id, product_name_snapshot, sku_snapshot, quantity, unit_price, mrp_snapshot, discount, tax_amount, line_total) VALUES
    (@o3, (SELECT id FROM products WHERE sku='SHIFA-002'), 'Triphala Powder', 'SHIFA-002', 3, 249.00, 299.00, 0.00, 0.00, 747.00),
    (@o3, (SELECT id FROM products WHERE sku='SHIFA-008'), 'Neem Capsules',   'SHIFA-008', 1, 349.00, 399.00, 0.00, 0.00, 349.00);
INSERT INTO payment_records (amount, payment_mode, transaction_reference, payment_date, notes, recorded_by, order_id)
VALUES (1150.80, 'ONLINE', 'TXN-DEMO-0003', TIMESTAMP(@d_m3,'16:05:00'), '[DEMOSEED]', 1, @o3);
INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, notes, changed_at) VALUES
    (@o3, NULL, 'NEW', 1, '[DEMOSEED] created', TIMESTAMP(@d_m3,'08:45:00')),
    (@o3, 'NEW', 'DELIVERED', 1, '[DEMOSEED] fulfilled', TIMESTAMP(DATE_ADD(@d_m3, INTERVAL 3 DAY),'13:00:00'));

-- ===== O4  DELIVERED  (TODAY)  c4 / S01 ==============================
-- items: SHIFA-013 x2 (398) + SHIFA-018 x1 (219) = 617 ; tax 30.85 ; ship 50
INSERT INTO orders (order_number, customer_id, salesperson_id, order_source, status,
    subtotal, discount_amount, tax_amount, shipping_charge, total_amount,
    payment_mode, payment_status, notes, order_date, dispatched_at, delivered_at)
VALUES (CONCAT('ORD-', DATE_FORMAT(@d_today,'%Y%m%d'), '-0004'),
    (SELECT id FROM customers WHERE email='lakshmi@shifademo.test'),
    (SELECT id FROM salespersons WHERE employee_code='DEMO-S01'),
    'MANUAL', 'DELIVERED', 617.00, 0.00, 30.85, 50.00, 697.85,
    'UPI', 'PAID', '[DEMOSEED] Delivered today', @d_today,
    TIMESTAMP(@d_today,'09:00:00'), TIMESTAMP(@d_today,'15:00:00'));
SET @o4 = LAST_INSERT_ID();
INSERT INTO order_items (order_id, product_id, product_name_snapshot, sku_snapshot, quantity, unit_price, mrp_snapshot, discount, tax_amount, line_total) VALUES
    (@o4, (SELECT id FROM products WHERE sku='SHIFA-013'), 'Arjuna Tea',      'SHIFA-013', 2, 199.00, 225.00, 0.00, 0.00, 398.00),
    (@o4, (SELECT id FROM products WHERE sku='SHIFA-018'), 'Haritaki Powder', 'SHIFA-018', 1, 219.00, 249.00, 0.00, 0.00, 219.00);
INSERT INTO payment_records (amount, payment_mode, transaction_reference, payment_date, notes, recorded_by, order_id)
VALUES (697.85, 'UPI', 'TXN-DEMO-0004', TIMESTAMP(@d_today,'09:05:00'), '[DEMOSEED]', 1, @o4);
INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, notes, changed_at) VALUES
    (@o4, NULL, 'NEW', 1, '[DEMOSEED] created', TIMESTAMP(@d_today,'08:00:00')),
    (@o4, 'NEW', 'DELIVERED', 1, '[DEMOSEED] fulfilled', TIMESTAMP(@d_today,'15:00:00'));

-- ===== O5  DELIVERED  (TODAY)  c5 / S02 ==============================
-- items: SHIFA-004 x2 (598) + SHIFA-005 x2 (338) = 936 ; tax 46.80 ; ship 40
INSERT INTO orders (order_number, customer_id, salesperson_id, order_source, status,
    subtotal, discount_amount, tax_amount, shipping_charge, total_amount,
    payment_mode, payment_status, notes, order_date, dispatched_at, delivered_at)
VALUES (CONCAT('ORD-', DATE_FORMAT(@d_today,'%Y%m%d'), '-0005'),
    (SELECT id FROM customers WHERE email='kiran@shifademo.test'),
    (SELECT id FROM salespersons WHERE employee_code='DEMO-S02'),
    'WHATSAPP', 'DELIVERED', 936.00, 0.00, 46.80, 40.00, 1022.80,
    'ONLINE', 'PAID', '[DEMOSEED] Delivered today', @d_today,
    TIMESTAMP(@d_today,'10:00:00'), TIMESTAMP(@d_today,'16:30:00'));
SET @o5 = LAST_INSERT_ID();
INSERT INTO order_items (order_id, product_id, product_name_snapshot, sku_snapshot, quantity, unit_price, mrp_snapshot, discount, tax_amount, line_total) VALUES
    (@o5, (SELECT id FROM products WHERE sku='SHIFA-004'), 'Giloy Juice', 'SHIFA-004', 2, 299.00, 350.00, 0.00, 0.00, 598.00),
    (@o5, (SELECT id FROM products WHERE sku='SHIFA-005'), 'Tulsi Drops', 'SHIFA-005', 2, 169.00, 199.00, 0.00, 0.00, 338.00);
INSERT INTO payment_records (amount, payment_mode, transaction_reference, payment_date, notes, recorded_by, order_id)
VALUES (1022.80, 'ONLINE', 'TXN-DEMO-0005', TIMESTAMP(@d_today,'10:05:00'), '[DEMOSEED]', 1, @o5);
INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, notes, changed_at) VALUES
    (@o5, NULL, 'NEW', 1, '[DEMOSEED] created', TIMESTAMP(@d_today,'09:15:00')),
    (@o5, 'NEW', 'DELIVERED', 1, '[DEMOSEED] fulfilled', TIMESTAMP(@d_today,'16:30:00'));

-- ===== O6  NEW  (TODAY)  c6 / S03 ====================================
-- items: SHIFA-001 x1 (499) + SHIFA-008 x1 (349) = 848 ; tax 42.40 ; ship 50
INSERT INTO orders (order_number, customer_id, salesperson_id, order_source, status,
    subtotal, discount_amount, tax_amount, shipping_charge, total_amount,
    payment_mode, payment_status, notes, order_date)
VALUES (CONCAT('ORD-', DATE_FORMAT(@d_today,'%Y%m%d'), '-0006'),
    (SELECT id FROM customers WHERE email='sunita@shifademo.test'),
    (SELECT id FROM salespersons WHERE employee_code='DEMO-S03'),
    'WHATSAPP', 'NEW', 848.00, 0.00, 42.40, 50.00, 940.40,
    'COD', 'PENDING', '[DEMOSEED] New order today', @d_today);
SET @o6 = LAST_INSERT_ID();
INSERT INTO order_items (order_id, product_id, product_name_snapshot, sku_snapshot, quantity, unit_price, mrp_snapshot, discount, tax_amount, line_total) VALUES
    (@o6, (SELECT id FROM products WHERE sku='SHIFA-001'), 'Ashwagandha Capsules', 'SHIFA-001', 1, 499.00, 599.00, 0.00, 0.00, 499.00),
    (@o6, (SELECT id FROM products WHERE sku='SHIFA-008'), 'Neem Capsules',        'SHIFA-008', 1, 349.00, 399.00, 0.00, 0.00, 349.00);
INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, notes, changed_at) VALUES
    (@o6, NULL, 'NEW', 1, '[DEMOSEED] created', TIMESTAMP(@d_today,'11:00:00'));

-- ===== O7  CONFIRMED  (TODAY)  c7 / S01 ==============================
-- items: SHIFA-011 x1 (849) + SHIFA-015 x1 (159) = 1008 ; tax 50.40 ; ship 50
INSERT INTO orders (order_number, customer_id, salesperson_id, order_source, status,
    subtotal, discount_amount, tax_amount, shipping_charge, total_amount,
    payment_mode, payment_status, notes, order_date)
VALUES (CONCAT('ORD-', DATE_FORMAT(@d_today,'%Y%m%d'), '-0007'),
    (SELECT id FROM customers WHERE email='manoj@shifademo.test'),
    (SELECT id FROM salespersons WHERE employee_code='DEMO-S01'),
    'MANUAL', 'CONFIRMED', 1008.00, 0.00, 50.40, 50.00, 1108.40,
    'UPI', 'PENDING', '[DEMOSEED] Confirmed today', @d_today);
SET @o7 = LAST_INSERT_ID();
INSERT INTO order_items (order_id, product_id, product_name_snapshot, sku_snapshot, quantity, unit_price, mrp_snapshot, discount, tax_amount, line_total) VALUES
    (@o7, (SELECT id FROM products WHERE sku='SHIFA-011'), 'Shilajit Resin',  'SHIFA-011', 1, 849.00, 999.00, 0.00, 0.00, 849.00),
    (@o7, (SELECT id FROM products WHERE sku='SHIFA-015'), 'Trikatu Churna',  'SHIFA-015', 1, 159.00, 189.00, 0.00, 0.00, 159.00);
INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, notes, changed_at) VALUES
    (@o7, NULL, 'NEW', 1, '[DEMOSEED] created', TIMESTAMP(@d_today,'11:30:00')),
    (@o7, 'NEW', 'CONFIRMED', 1, '[DEMOSEED] confirmed', TIMESTAMP(@d_today,'12:00:00'));

-- ===== O8  NEW  (TODAY)  c8 / no salesperson =========================
-- items: SHIFA-006 x1 (129) + SHIFA-017 x1 (399) + SHIFA-013 x1 (199) = 727 ; tax 36.35 ; ship 50
INSERT INTO orders (order_number, customer_id, salesperson_id, order_source, status,
    subtotal, discount_amount, tax_amount, shipping_charge, total_amount,
    payment_mode, payment_status, notes, order_date)
VALUES (CONCAT('ORD-', DATE_FORMAT(@d_today,'%Y%m%d'), '-0008'),
    (SELECT id FROM customers WHERE email='deepa@shifademo.test'),
    NULL,
    'STOREFRONT', 'NEW', 727.00, 0.00, 36.35, 50.00, 813.35,
    'COD', 'PENDING', '[DEMOSEED] New order today', @d_today);
SET @o8 = LAST_INSERT_ID();
INSERT INTO order_items (order_id, product_id, product_name_snapshot, sku_snapshot, quantity, unit_price, mrp_snapshot, discount, tax_amount, line_total) VALUES
    (@o8, (SELECT id FROM products WHERE sku='SHIFA-006'), 'Amla Candy',       'SHIFA-006', 1, 129.00, 150.00, 0.00, 0.00, 129.00),
    (@o8, (SELECT id FROM products WHERE sku='SHIFA-017'), 'Haridra Capsules', 'SHIFA-017', 1, 399.00, 449.00, 0.00, 0.00, 399.00),
    (@o8, (SELECT id FROM products WHERE sku='SHIFA-013'), 'Arjuna Tea',       'SHIFA-013', 1, 199.00, 225.00, 0.00, 0.00, 199.00);
INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, notes, changed_at) VALUES
    (@o8, NULL, 'NEW', 1, '[DEMOSEED] created', TIMESTAMP(@d_today,'12:30:00'));

-- ===== O9  PAID  (this month) -> dispatch queue  c1 / S02 ============
-- items: SHIFA-012 x3 (987) = 987 ; disc 47 ; tax 49.35 ; ship 0
INSERT INTO orders (order_number, customer_id, salesperson_id, order_source, status,
    subtotal, discount_amount, tax_amount, shipping_charge, total_amount,
    payment_mode, payment_status, notes, order_date)
VALUES (CONCAT('ORD-', DATE_FORMAT(@d_m4,'%Y%m%d'), '-0009'),
    (SELECT id FROM customers WHERE email='ravi@shifademo.test'),
    (SELECT id FROM salespersons WHERE employee_code='DEMO-S02'),
    'MANUAL', 'PAID', 987.00, 47.00, 49.35, 0.00, 989.35,
    'UPI', 'PAID', '[DEMOSEED] Paid - awaiting packing', @d_m4);
SET @o9 = LAST_INSERT_ID();
INSERT INTO order_items (order_id, product_id, product_name_snapshot, sku_snapshot, quantity, unit_price, mrp_snapshot, discount, tax_amount, line_total) VALUES
    (@o9, (SELECT id FROM products WHERE sku='SHIFA-012'), 'Moringa Powder', 'SHIFA-012', 3, 329.00, 375.00, 0.00, 0.00, 987.00);
INSERT INTO payment_records (amount, payment_mode, transaction_reference, payment_date, notes, recorded_by, order_id)
VALUES (989.35, 'UPI', 'TXN-DEMO-0009', TIMESTAMP(@d_m4,'13:00:00'), '[DEMOSEED]', 1, @o9);
INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, notes, changed_at) VALUES
    (@o9, NULL, 'NEW', 1, '[DEMOSEED] created', TIMESTAMP(@d_m4,'10:00:00')),
    (@o9, 'NEW', 'PAID', 1, '[DEMOSEED] payment received', TIMESTAMP(@d_m4,'13:00:00'));

-- ===== O10  PAID  (TODAY) -> dispatch queue  c2 / S03 ================
-- items: SHIFA-016 x1 (479) + SHIFA-014 x1 (799) = 1278 ; tax 63.90 ; ship 50
INSERT INTO orders (order_number, customer_id, salesperson_id, order_source, status,
    subtotal, discount_amount, tax_amount, shipping_charge, total_amount,
    payment_mode, payment_status, notes, order_date)
VALUES (CONCAT('ORD-', DATE_FORMAT(@d_today,'%Y%m%d'), '-0010'),
    (SELECT id FROM customers WHERE email='priya@shifademo.test'),
    (SELECT id FROM salespersons WHERE employee_code='DEMO-S03'),
    'WHATSAPP', 'PAID', 1278.00, 0.00, 63.90, 50.00, 1391.90,
    'ONLINE', 'PAID', '[DEMOSEED] Paid today - awaiting packing', @d_today);
SET @o10 = LAST_INSERT_ID();
INSERT INTO order_items (order_id, product_id, product_name_snapshot, sku_snapshot, quantity, unit_price, mrp_snapshot, discount, tax_amount, line_total) VALUES
    (@o10, (SELECT id FROM products WHERE sku='SHIFA-016'), 'Shatavari Capsules', 'SHIFA-016', 1, 479.00, 549.00, 0.00, 0.00, 479.00),
    (@o10, (SELECT id FROM products WHERE sku='SHIFA-014'), 'Kumkumadi Oil',      'SHIFA-014', 1, 799.00, 899.00, 0.00, 0.00, 799.00);
INSERT INTO payment_records (amount, payment_mode, transaction_reference, payment_date, notes, recorded_by, order_id)
VALUES (1391.90, 'ONLINE', 'TXN-DEMO-0010', TIMESTAMP(@d_today,'11:00:00'), '[DEMOSEED]', 1, @o10);
INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, notes, changed_at) VALUES
    (@o10, NULL, 'NEW', 1, '[DEMOSEED] created', TIMESTAMP(@d_today,'09:45:00')),
    (@o10, 'NEW', 'PAID', 1, '[DEMOSEED] payment received', TIMESTAMP(@d_today,'11:00:00'));

-- ===== O11  PACKED  (this month) -> dispatch queue  c3 / S01 =========
-- items: SHIFA-007 x2 (498) + SHIFA-008 x2 (698) = 1196 ; disc 20 ; tax 59.80 ; ship 0
INSERT INTO orders (order_number, customer_id, salesperson_id, order_source, status,
    subtotal, discount_amount, tax_amount, shipping_charge, total_amount,
    payment_mode, payment_status, notes, order_date)
VALUES (CONCAT('ORD-', DATE_FORMAT(@d_m4,'%Y%m%d'), '-0011'),
    (SELECT id FROM customers WHERE email='amit@shifademo.test'),
    (SELECT id FROM salespersons WHERE employee_code='DEMO-S01'),
    'MANUAL', 'PACKED', 1196.00, 20.00, 59.80, 0.00, 1235.80,
    'UPI', 'PAID', '[DEMOSEED] Packed - ready to dispatch', @d_m4);
SET @o11 = LAST_INSERT_ID();
INSERT INTO order_items (order_id, product_id, product_name_snapshot, sku_snapshot, quantity, unit_price, mrp_snapshot, discount, tax_amount, line_total) VALUES
    (@o11, (SELECT id FROM products WHERE sku='SHIFA-007'), 'Brahmi Syrup',  'SHIFA-007', 2, 249.00, 275.00, 0.00, 0.00, 498.00),
    (@o11, (SELECT id FROM products WHERE sku='SHIFA-008'), 'Neem Capsules', 'SHIFA-008', 2, 349.00, 399.00, 0.00, 0.00, 698.00);
INSERT INTO payment_records (amount, payment_mode, transaction_reference, payment_date, notes, recorded_by, order_id)
VALUES (1235.80, 'UPI', 'TXN-DEMO-0011', TIMESTAMP(@d_m4,'14:00:00'), '[DEMOSEED]', 1, @o11);
INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, notes, changed_at) VALUES
    (@o11, NULL, 'NEW', 1, '[DEMOSEED] created', TIMESTAMP(@d_m4,'09:00:00')),
    (@o11, 'NEW', 'PAID', 1, '[DEMOSEED] payment received', TIMESTAMP(@d_m4,'14:00:00')),
    (@o11, 'PAID', 'PACKED', 1, '[DEMOSEED] packed', TIMESTAMP(@d_m4,'17:00:00'));

-- ===== O12  PACKED  (TODAY) -> dispatch queue  c4 / S02 ==============
-- items: SHIFA-001 x1 (499) + SHIFA-003 x1 (399) = 898 ; tax 44.90 ; ship 50
INSERT INTO orders (order_number, customer_id, salesperson_id, order_source, status,
    subtotal, discount_amount, tax_amount, shipping_charge, total_amount,
    payment_mode, payment_status, notes, order_date)
VALUES (CONCAT('ORD-', DATE_FORMAT(@d_today,'%Y%m%d'), '-0012'),
    (SELECT id FROM customers WHERE email='lakshmi@shifademo.test'),
    (SELECT id FROM salespersons WHERE employee_code='DEMO-S02'),
    'STOREFRONT', 'PACKED', 898.00, 0.00, 44.90, 50.00, 992.90,
    'ONLINE', 'PAID', '[DEMOSEED] Packed today - ready to dispatch', @d_today);
SET @o12 = LAST_INSERT_ID();
INSERT INTO order_items (order_id, product_id, product_name_snapshot, sku_snapshot, quantity, unit_price, mrp_snapshot, discount, tax_amount, line_total) VALUES
    (@o12, (SELECT id FROM products WHERE sku='SHIFA-001'), 'Ashwagandha Capsules', 'SHIFA-001', 1, 499.00, 599.00, 0.00, 0.00, 499.00),
    (@o12, (SELECT id FROM products WHERE sku='SHIFA-003'), 'Chyawanprash',         'SHIFA-003', 1, 399.00, 450.00, 0.00, 0.00, 399.00);
INSERT INTO payment_records (amount, payment_mode, transaction_reference, payment_date, notes, recorded_by, order_id)
VALUES (992.90, 'ONLINE', 'TXN-DEMO-0012', TIMESTAMP(@d_today,'10:30:00'), '[DEMOSEED]', 1, @o12);
INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, notes, changed_at) VALUES
    (@o12, NULL, 'NEW', 1, '[DEMOSEED] created', TIMESTAMP(@d_today,'08:30:00')),
    (@o12, 'NEW', 'PAID', 1, '[DEMOSEED] payment received', TIMESTAMP(@d_today,'10:30:00')),
    (@o12, 'PAID', 'PACKED', 1, '[DEMOSEED] packed', TIMESTAMP(@d_today,'13:00:00'));

-- ===== O13  DISPATCHED  (this month)  c5 / S03 =======================
-- items: SHIFA-009 x1 (375) + SHIFA-010 x1 (289) = 664 ; tax 33.20 ; ship 50
INSERT INTO orders (order_number, customer_id, salesperson_id, order_source, status,
    subtotal, discount_amount, tax_amount, shipping_charge, total_amount,
    payment_mode, payment_status, notes, order_date, dispatched_at)
VALUES (CONCAT('ORD-', DATE_FORMAT(@d_m2,'%Y%m%d'), '-0013'),
    (SELECT id FROM customers WHERE email='kiran@shifademo.test'),
    (SELECT id FROM salespersons WHERE employee_code='DEMO-S03'),
    'WHATSAPP', 'DISPATCHED', 664.00, 0.00, 33.20, 50.00, 747.20,
    'UPI', 'PAID', '[DEMOSEED] In transit', @d_m2, TIMESTAMP(DATE_ADD(@d_m2, INTERVAL 1 DAY),'10:00:00'));
SET @o13 = LAST_INSERT_ID();
INSERT INTO order_items (order_id, product_id, product_name_snapshot, sku_snapshot, quantity, unit_price, mrp_snapshot, discount, tax_amount, line_total) VALUES
    (@o13, (SELECT id FROM products WHERE sku='SHIFA-009'), 'Karela Jamun Juice', 'SHIFA-009', 1, 375.00, 425.00, 0.00, 0.00, 375.00),
    (@o13, (SELECT id FROM products WHERE sku='SHIFA-010'), 'Aloe Vera Juice',    'SHIFA-010', 1, 289.00, 325.00, 0.00, 0.00, 289.00);
INSERT INTO payment_records (amount, payment_mode, transaction_reference, payment_date, notes, recorded_by, order_id)
VALUES (747.20, 'UPI', 'TXN-DEMO-0013', TIMESTAMP(@d_m2,'12:00:00'), '[DEMOSEED]', 1, @o13);
INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, notes, changed_at) VALUES
    (@o13, NULL, 'NEW', 1, '[DEMOSEED] created', TIMESTAMP(@d_m2,'09:00:00')),
    (@o13, 'NEW', 'PAID', 1, '[DEMOSEED] payment received', TIMESTAMP(@d_m2,'12:00:00')),
    (@o13, 'PAID', 'PACKED', 1, '[DEMOSEED] packed', TIMESTAMP(@d_m2,'16:00:00')),
    (@o13, 'PACKED', 'DISPATCHED', 1, '[DEMOSEED] dispatched', TIMESTAMP(DATE_ADD(@d_m2, INTERVAL 1 DAY),'10:00:00'));

-- ===== O14  CANCELLED  (this month)  c6 / no salesperson =============
-- items: SHIFA-005 x1 (169) = 169 ; tax 8.45 ; ship 50
INSERT INTO orders (order_number, customer_id, salesperson_id, order_source, status,
    subtotal, discount_amount, tax_amount, shipping_charge, total_amount,
    payment_mode, payment_status, notes, order_date)
VALUES (CONCAT('ORD-', DATE_FORMAT(@d_m3,'%Y%m%d'), '-0014'),
    (SELECT id FROM customers WHERE email='sunita@shifademo.test'),
    NULL,
    'WHATSAPP', 'CANCELLED', 169.00, 0.00, 8.45, 50.00, 227.45,
    'COD', 'PENDING', '[DEMOSEED] Cancelled by customer', @d_m3);
SET @o14 = LAST_INSERT_ID();
INSERT INTO order_items (order_id, product_id, product_name_snapshot, sku_snapshot, quantity, unit_price, mrp_snapshot, discount, tax_amount, line_total) VALUES
    (@o14, (SELECT id FROM products WHERE sku='SHIFA-005'), 'Tulsi Drops', 'SHIFA-005', 1, 169.00, 199.00, 0.00, 0.00, 169.00);
INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, notes, changed_at) VALUES
    (@o14, NULL, 'NEW', 1, '[DEMOSEED] created', TIMESTAMP(@d_m3,'10:00:00')),
    (@o14, 'NEW', 'CANCELLED', 1, '[DEMOSEED] cancelled', TIMESTAMP(@d_m3,'11:00:00'));

-- ===== O15  DELIVERED  (previous month)  c7 / S01 ====================
-- items: SHIFA-004 x1 (299) + SHIFA-006 x1 (129) + SHIFA-012 x1 (329) = 757 ; tax 37.85 ; ship 50
INSERT INTO orders (order_number, customer_id, salesperson_id, order_source, status,
    subtotal, discount_amount, tax_amount, shipping_charge, total_amount,
    payment_mode, payment_status, notes, order_date, dispatched_at, delivered_at)
VALUES (CONCAT('ORD-', DATE_FORMAT(@d_prev,'%Y%m%d'), '-0015'),
    (SELECT id FROM customers WHERE email='manoj@shifademo.test'),
    (SELECT id FROM salespersons WHERE employee_code='DEMO-S01'),
    'MANUAL', 'DELIVERED', 757.00, 0.00, 37.85, 50.00, 844.85,
    'COD', 'PAID', '[DEMOSEED] Delivered previous month', @d_prev,
    TIMESTAMP(DATE_ADD(@d_prev, INTERVAL 1 DAY),'10:00:00'), TIMESTAMP(DATE_ADD(@d_prev, INTERVAL 3 DAY),'14:00:00'));
SET @o15 = LAST_INSERT_ID();
INSERT INTO order_items (order_id, product_id, product_name_snapshot, sku_snapshot, quantity, unit_price, mrp_snapshot, discount, tax_amount, line_total) VALUES
    (@o15, (SELECT id FROM products WHERE sku='SHIFA-004'), 'Giloy Juice',    'SHIFA-004', 1, 299.00, 350.00, 0.00, 0.00, 299.00),
    (@o15, (SELECT id FROM products WHERE sku='SHIFA-006'), 'Amla Candy',     'SHIFA-006', 1, 129.00, 150.00, 0.00, 0.00, 129.00),
    (@o15, (SELECT id FROM products WHERE sku='SHIFA-012'), 'Moringa Powder', 'SHIFA-012', 1, 329.00, 375.00, 0.00, 0.00, 329.00);
INSERT INTO payment_records (amount, payment_mode, transaction_reference, payment_date, notes, recorded_by, order_id)
VALUES (844.85, 'COD', 'TXN-DEMO-0015', TIMESTAMP(DATE_ADD(@d_prev, INTERVAL 3 DAY),'14:00:00'), '[DEMOSEED]', 1, @o15);
INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, notes, changed_at) VALUES
    (@o15, NULL, 'NEW', 1, '[DEMOSEED] created', TIMESTAMP(@d_prev,'09:00:00')),
    (@o15, 'NEW', 'DELIVERED', 1, '[DEMOSEED] fulfilled', TIMESTAMP(DATE_ADD(@d_prev, INTERVAL 3 DAY),'14:00:00'));

-- =====================================================================
-- End of demo seed. No products are created or modified by this script;
-- all order items reference the real SHIFA-001..SHIFA-018 catalogue.
-- =====================================================================
