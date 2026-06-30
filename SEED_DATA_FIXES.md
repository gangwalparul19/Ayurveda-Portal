# SALES_TEAM_SEED_DATA.sql - Fixes Applied

## Issues Found and Fixed

### 1. **Wrong Column Names in salesperson_targets INSERT**
**Problem**: The seed data was trying to insert into columns that don't exist in the schema:
- `target_tier1`
- `target_tier2`
- `target_tier3`
- `tier_achieved`
- `salesperson_id` (instead of `salesperson_user_id`)

**Actual Schema** (from V1__create_tenant_schema.sql):
```sql
CREATE TABLE salesperson_targets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    salesperson_user_id BIGINT NOT NULL,
    month INT NOT NULL,
    year INT NOT NULL,
    target_amount DECIMAL(12,2),
    achieved_amount DECIMAL(12,2),
    ...
)
```

**Fix Applied**: Updated INSERT statements to use only existing columns:
```sql
INSERT INTO salesperson_targets (salesperson_user_id, month, year, target_amount, achieved_amount)
VALUES
(@sp001_id, 6, 2026, 50000.00, 48500.00),
...
```

### 2. **Complex SQL Syntax in Orders INSERT**
**Problem**: The INSERT statement used:
- `ROW_NUMBER() OVER ()` - Not supported in older MySQL versions
- Complex `CASE` statements with `RAND()` 
- Multiple conditional logic

**Fix Applied**: Simplified to basic INSERT...SELECT statements with static values:
```sql
INSERT INTO orders (order_number, order_date, customer_id, salesperson_id, ...)
SELECT 'ORD-SP001-20260627-001', '2026-06-27', @customer_1_id, @sp001_id, ...
WHERE @customer_1_id IS NOT NULL;
```

### 3. **Double Semicolon**
**Problem**: Line 81 had `;;` instead of `;`

**Fix Applied**: Changed to single semicolon

## Steps to Run the Fixed Seed Data

1. **Prerequisite**: Run SEED_DEMO_DATA.sql first to create customers and products
2. **Run the corrected seed data**:
   ```bash
   mysql -h localhost -u root -p shifa_db < SALES_TEAM_SEED_DATA.sql
   ```

## What Gets Inserted

### Platform Users (from master database)
- saleshead (MANAGER role)
- sp001 - sp005 (SALESPERSON role)

### Salespersons (in tenant database)
- 5 salespersons linked to platform users with commission rates

### Salesperson Targets (May & June 2026)
- Monthly targets and achieved amounts for each salesperson

### Salesperson Performance (June 2026)
- Daily performance metrics for June 23-29, 2026

### Sample Orders
- 3 sample orders assigned to sp001 and sp002
- Demonstrates order filtering by salesperson

## Verification Queries

After running the seed data, verify the data is correctly inserted:

```sql
-- Check salespersons
SELECT * FROM salespersons;

-- Check targets
SELECT s.employee_code, st.* FROM salesperson_targets st
JOIN salespersons s ON st.salesperson_user_id = s.id;

-- Check performance data
SELECT s.employee_code, sp.* FROM salesperson_performance sp
JOIN salespersons s ON sp.salesperson_id = s.id
LIMIT 20;

-- Check orders
SELECT o.order_number, c.name, s.name 
FROM orders o
LEFT JOIN customers c ON o.customer_id = c.id
LEFT JOIN salespersons s ON o.salesperson_id = s.id
WHERE s.id IS NOT NULL;
```

## Login Credentials

After running the seed data:

| Username | Password | Role |
|----------|----------|------|
| sp001 | admin123 | SALESPERSON |
| sp002 | admin123 | SALESPERSON |
| sp003 | admin123 | SALESPERSON |
| sp004 | admin123 | SALESPERSON |
| sp005 | admin123 | SALESPERSON |
| saleshead | admin123 | MANAGER |

## Notes

- The `salesperson_performance` table requires separate management for dashboard data
- Tier-based targets (tier1, tier2, tier3) are not part of the current schema
- To add tier-based targets, the schema would need to be extended via a new migration
- Sample orders are created with conditional WHERE clauses to prevent errors if customers don't exist
