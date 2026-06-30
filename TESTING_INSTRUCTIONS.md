# Testing Instructions - Sales Performance Feature

## Step 1: Import Seed Data

### Option A: Using MySQL Workbench
1. Open MySQL Workbench
2. Connect to your local database (localhost:3306)
3. Select database: `shifa_db`
4. Go to File → Run SQL Script
5. Select: `SALES_TEAM_SEED_DATA.sql`
6. Click **Run**

### Option B: Using Command Line
```bash
# Navigate to project directory
cd "c:\E Drive\E-Commerce\Ayurveda Portal"

# Run the seed data (adjust MySQL path if needed)
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -proot@123 shifa_db < SALES_TEAM_SEED_DATA.sql
```

### Option C: Copy-Paste in MySQL Workbench
1. Open `SALES_TEAM_SEED_DATA.sql`
2. Copy all content
3. Paste into MySQL Workbench query window
4. Execute (Ctrl+Shift+Enter)

## Step 2: Start the Backend

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

Wait for: `Started AyurvedaPlatformApplication`

## Step 3: Test the APIs

### Test 1: Login as Salesperson
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"sp001\",\"password\":\"Sales@123\"}"
```

Expected response:
```json
{
  "accessToken": "eyJhbGc...",
  "username": "sp001",
  "fullName": "Priya Sharma",
  "role": "SALESPERSON",
  ...
}
```

**Copy the `accessToken` value** - you'll need it for next requests!

### Test 2: Get Salesperson ID

First, we need to find the salesperson record ID. Login as admin:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
```

Then get salesperson by employee code:

```bash
curl -X GET "http://localhost:8080/api/salesperson/by-employee-code/SP001" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

Note the `id` field (probably 1 or 2).

### Test 3: Get Dashboard

Replace `{salespersonId}` with the ID from above and `{token}` with the salesperson's token:

```bash
curl -X GET "http://localhost:8080/api/salesperson-dashboard/{salespersonId}" \
  -H "Authorization: Bearer {token}"
```

Expected response:
```json
{
  "salespersonInfo": {
    "id": 1,
    "name": "Priya Sharma",
    "employeeCode": "SP001",
    "commissionRate": 2.50,
    "status": "ACTIVE"
  },
  "todayPerformance": {
    "date": "2026-06-29",
    "ordersCount": 12,
    "totalSales": 17400.00,
    "totalItemsSold": 61,
    "commissionEarned": 435.00
  },
  "weekPerformance": {
    "startDate": "2026-06-23",
    "endDate": "2026-06-29",
    "ordersCount": 70,
    "totalSales": 104100.00,
    "totalItemsSold": 409
  },
  "monthPerformance": {
    "month": 6,
    "year": 2026,
    "ordersCount": 71,
    "totalSales": 117900.00,
    "totalItemsSold": 457
  },
  "targetAchievement": {
    "hasTarget": true,
    "targetTier1": 40000.00,
    "targetTier2": 50000.00,
    "targetTier3": 60000.00,
    "achievedAmount": 48500.00,
    "tierAchieved": 2,
    "percentageTier1": 121.25,
    "percentageTier2": 97.00,
    "percentageTier3": 80.83,
    "status": "Tier 2 Achieved! 🥈"
  },
  "recentTrend": [
    { "date": "2026-06-23", "totalSales": 12500.00, "ordersCount": 8 },
    { "date": "2026-06-24", "totalSales": 14800.00, "ordersCount": 10 },
    ...
  ]
}
```

### Test 4: Get Weekly Leaderboard

```bash
curl -X GET "http://localhost:8080/api/salesperson-dashboard/leaderboard/week" \
  -H "Authorization: Bearer {token}"
```

Expected response:
```json
[{
  "periodType": "week",
  "startDate": "2026-06-23",
  "endDate": "2026-06-29",
  "rankings": [
    {
      "rank": 1,
      "salespersonId": 4,
      "name": "Vikram Reddy",
      "employeeCode": "SP004",
      "totalSales": 112700.00,
      "ordersCount": 77,
      "itemsSold": 398,
      "medal": "🥇",
      "award": "Gold"
    },
    {
      "rank": 2,
      "salespersonId": 1,
      "name": "Priya Sharma",
      "employeeCode": "SP001",
      "totalSales": 104100.00,
      "ordersCount": 70,
      "itemsSold": 409,
      "medal": "🥈",
      "award": "Silver"
    },
    ...
  ]
}]
```

### Test 5: Get My Rank

```bash
curl -X GET "http://localhost:8080/api/salesperson-dashboard/{salespersonId}/my-rank/week" \
  -H "Authorization: Bearer {token}"
```

Expected response:
```json
{
  "found": true,
  "rank": 2,
  "totalSales": 104100.00,
  "ordersCount": 70,
  "medal": "🥈",
  "award": "Silver",
  "totalParticipants": 5,
  "period": "week"
}
```

### Test 6: Set New Target (Admin Only)

Login as admin first, then:

```bash
curl -X POST http://localhost:8080/api/salesperson/targets \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {admin_token}" \
  -d "{\"salespersonId\":1,\"month\":7,\"year\":2026,\"targetAmount\":55000,\"targetTier1\":45000,\"targetTier2\":55000,\"targetTier3\":65000}"
```

## Step 4: Verify Data in Database

Open MySQL Workbench and run these queries:

### Check Salespersons
```sql
SELECT * FROM salespersons;
```

Should show 5 salespersons: SP001 through SP005.

### Check Targets
```sql
SELECT 
    st.*,
    s.name as salesperson_name,
    CASE 
        WHEN st.tier_achieved = 3 THEN 'Tier 3 🏆'
        WHEN st.tier_achieved = 2 THEN 'Tier 2 🥈'
        WHEN st.tier_achieved = 1 THEN 'Tier 1 🥉'
        ELSE 'Below Target'
    END as status
FROM salesperson_targets st
JOIN salespersons s ON st.salesperson_user_id = s.id
WHERE st.month = 6 AND st.year = 2026;
```

Expected to show 5 targets with various achievement levels.

### Check Performance Data
```sql
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
```

Should show performance data for the last week.

### Current Leaderboard
```sql
SELECT 
    ROW_NUMBER() OVER (ORDER BY SUM(sp.total_sales) DESC) as `rank`,
    s.employee_code,
    s.name,
    COUNT(sp.id) as days_worked,
    SUM(sp.orders_count) as total_orders,
    SUM(sp.total_sales) as total_sales,
    CASE 
        WHEN ROW_NUMBER() OVER (ORDER BY SUM(sp.total_sales) DESC) = 1 THEN '🥇 Gold'
        WHEN ROW_NUMBER() OVER (ORDER BY SUM(sp.total_sales) DESC) = 2 THEN '🥈 Silver'
        WHEN ROW_NUMBER() OVER (ORDER BY SUM(sp.total_sales) DESC) = 3 THEN '🥉 Bronze'
        ELSE ''
    END as award
FROM salespersons s
LEFT JOIN salesperson_performance sp ON s.id = sp.salesperson_id
WHERE sp.performance_date >= '2026-06-23'  -- Current week
GROUP BY s.id, s.employee_code, s.name
ORDER BY total_sales DESC;
```

Expected Rankings:
1. Vikram Reddy (SP004) - ₹112,700 🥇
2. Priya Sharma (SP001) - ₹104,100 🥈
3. Amit Patel (SP002) - ₹83,500 🥉
4. Neha Singh (SP003) - ₹69,500
5. Anita Desai (SP005) - ₹52,800

## Step 5: Test Different Salesperson Logins

Try logging in as different salespersons to see varied dashboards:

| Username | Password | Performance Level | Target Status |
|----------|----------|-------------------|---------------|
| sp001 | Sales@123 | High (₹104K/week) | Tier 2 Achieved 🥈 |
| sp002 | Sales@123 | Medium (₹83K/week) | Tier 1 Achieved 🥉 |
| sp003 | Sales@123 | Medium (₹69K/week) | Tier 1 Achieved 🥉 |
| sp004 | Sales@123 | Highest (₹112K/week) | Tier 3 Achieved 🏆 |
| sp005 | Sales@123 | New (₹52K/week) | Below Target |

## Troubleshooting

### Issue: salesperson_performance table doesn't exist
Hibernate should auto-create it. If not, run:
```sql
CREATE TABLE IF NOT EXISTS salesperson_performance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    salesperson_id BIGINT NOT NULL,
    performance_date DATE NOT NULL,
    orders_count INT NOT NULL DEFAULT 0,
    total_sales DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_items_sold INT NOT NULL DEFAULT 0,
    commission_earned DECIMAL(10,2) DEFAULT 0.00,
    INDEX idx_performance_salesperson_date (salesperson_id, performance_date),
    INDEX idx_performance_date (performance_date)
);
```

### Issue: salesperson_targets missing new columns
Run:
```sql
ALTER TABLE salesperson_targets 
ADD COLUMN IF NOT EXISTS target_tier1 DECIMAL(12,2) DEFAULT 0.00,
ADD COLUMN IF NOT EXISTS target_tier2 DECIMAL(12,2) DEFAULT 0.00,
ADD COLUMN IF NOT EXISTS target_tier3 DECIMAL(12,2) DEFAULT 0.00,
ADD COLUMN IF NOT EXISTS tier_achieved INT;
```

### Issue: 404 on endpoints
Check that all services are injected properly and backend started without errors.

### Issue: 403 Forbidden
Make sure you're using the correct role's token. Salesperson endpoints require SALESPERSON or TENANT_ADMIN role.

## Success Criteria

✅ All 5 salespersons can login
✅ Dashboard API returns complete data
✅ Leaderboard shows correct rankings with medals
✅ Target achievement shows tier status
✅ Performance trend shows last 7 days
✅ Admin can set new targets

## Next Steps

Once local testing is complete:
1. Commit all changes to Git
2. Push to GitHub
3. Railway will auto-deploy backend
4. Run seed SQL on Aiven cloud database
5. Test on production URLs
