# Cloud Database Setup Guide

## Issue: Missing Tables in Cloud Database

When running `SALES_TEAM_SEED_DATA.sql` on cloud databases (like Aiven), you may encounter:
```
Error Code: 1146. Table 'defaultdb.salesperson_performance' doesn't exist
```

This happens because not all tables were created during the initial database setup.

## Solution: Three-Step Setup Process

### Step 1: Create Missing Tables
Run this script first to ensure all required tables exist:

```bash
mysql -h [cloud-host] -u [user] -p[password] shifa_db < CREATE_MISSING_TABLES.sql
```

**What it does:**
- Creates the `salesperson_performance` table if it doesn't exist
- Verifies that `salespersons` and `salesperson_targets` tables exist
- Provides a status report of all required tables

### Step 2: Populate Seed Data
After confirming all tables exist, run the seed data script:

```bash
mysql -h [cloud-host] -u [user] -p[password] shifa_db < SALES_TEAM_SEED_DATA.sql
```

**What it does:**
- Creates platform users for salespersons (sp001-sp005)
- Creates salesperson records linked to users
- Inserts monthly targets for May & June 2026
- Inserts sample orders assigned to salespersons
- Performance data is commented out initially (uncomment after table verification)

### Step 3: Verify Setup
Check that everything was inserted correctly:

```sql
-- Check salespersons
SELECT COUNT(*) as salesperson_count FROM salespersons;

-- Check targets
SELECT COUNT(*) as target_count FROM salesperson_targets;

-- Check sample orders
SELECT COUNT(*) as order_count FROM orders WHERE salesperson_id IS NOT NULL;

-- Check performance data (if table exists)
SELECT COUNT(*) as performance_count FROM salesperson_performance;
```

## Troubleshooting

### Still Getting "Table Doesn't Exist" Error?

1. **Verify table creation succeeded:**
   ```sql
   SHOW TABLES LIKE 'salesperson_performance';
   ```
   
2. **If table is missing, create it manually:**
   ```sql
   USE shifa_db;
   
   CREATE TABLE salesperson_performance (
       id BIGINT AUTO_INCREMENT PRIMARY KEY,
       salesperson_id BIGINT NOT NULL,
       performance_date DATE NOT NULL,
       orders_count INT DEFAULT 0,
       total_sales DECIMAL(12,2) DEFAULT 0.00,
       total_items_sold INT DEFAULT 0,
       commission_earned DECIMAL(10,2) DEFAULT 0.00,
       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
       INDEX idx_performance_salesperson (salesperson_id),
       INDEX idx_performance_date (performance_date),
       CONSTRAINT fk_performance_salesperson FOREIGN KEY (salesperson_id) REFERENCES salespersons(id)
   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
   ```

3. **Then run the seed data again:**
   ```bash
   mysql -h [host] -u [user] -p[password] shifa_db < SALES_TEAM_SEED_DATA.sql
   ```

### Performance Data Not Showing?

The performance data section is commented out in `SALES_TEAM_SEED_DATA.sql` to prevent errors if the table doesn't exist.

To include performance data:

1. Verify the `salesperson_performance` table exists:
   ```sql
   SHOW TABLES LIKE 'salesperson_performance';
   ```

2. Open `SALES_TEAM_SEED_DATA.sql` and uncomment the performance data section (lines ~95-155):
   - Find: `/*` before `INSERT INTO salesperson_performance`
   - Find: `*/` after the last performance insert
   - Remove both comment markers

3. Run the seed script again

## Login Credentials

After successful setup:

| Username | Password | Role |
|----------|----------|------|
| sp001 | admin123 | SALESPERSON |
| sp002 | admin123 | SALESPERSON |
| sp003 | admin123 | SALESPERSON |
| sp004 | admin123 | SALESPERSON |
| sp005 | admin123 | SALESPERSON |
| saleshead | admin123 | MANAGER |

## What Each Salesperson Can See

After login with a salesperson account (e.g., sp001):

1. **Dashboard** (if performance data is populated)
   - Today's performance metrics
   - Weekly and monthly performance
   - Target achievement status

2. **Orders Page**
   - Only their assigned orders
   - Customer phone numbers are masked (e.g., `*3210`)
   - Full customer names and addresses are visible

3. **Dispatch Page** (if DISPATCHER role also assigned)
   - Orders ready for dispatch
   - Masked customer details

## Database Connection Details

For Aiven Cloud Database, use:
- **Host**: Your Aiven connection URL (e.g., `mysql-xxx.aivencloud.com`)
- **Port**: Usually `3306`
- **Username**: Your Aiven username
- **Password**: Your Aiven password
- **Database**: `shifa_db` (or your tenant database name)

Example command:
```bash
mysql -h mysql-abc123.aivencloud.com -u avnadmin -p -P 3306 shifa_db < SALES_TEAM_SEED_DATA.sql
```

## Next Steps

1. ✅ Run `CREATE_MISSING_TABLES.sql`
2. ✅ Run `SALES_TEAM_SEED_DATA.sql`
3. ✅ Verify data with SQL queries above
4. ✅ Login with salesperson credentials
5. ✅ Test order visibility and masking

If you encounter any other issues, check:
- Backend logs for JWT token generation errors
- Ensure salesperson record has correct `platform_user_id` 
- Verify OrderController is using correct salesperson lookup logic
