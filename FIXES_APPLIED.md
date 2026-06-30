# Fixes Applied - Sales Performance Feature

## Bug Fixes

### 1. SQL Syntax Errors Fixed ✅

**Problem:** SQL queries were failing with syntax errors.

**Fixed:**
- Escaped `rank` reserved keyword with backticks: `` `rank` ``
- Replaced inline subqueries in INSERT with SET variables
- Improved MySQL compatibility

**Files Changed:**
- `SALES_TEAM_SEED_DATA.sql` - All INSERT statements now use variables
- `TESTING_INSTRUCTIONS.md` - Updated example queries

### 2. Recalculate Target Bug Fixed ✅

**Problem:** Clicking "Recalculate" on the target page cleared all achievement data to zero instead of recalculating from actual sales.

**Root Cause:**
- The method was querying orders by `salesperson_id`, but if no orders matched or performance data was missing, it would set `achieved_amount` to 0
- Didn't properly aggregate from the `salesperson_performance` table

**Fixed:**
- Updated `recalculateAchievements()` to:
  1. First try to get sales from `salesperson_performance` table (primary source)
  2. Fall back to `orders` table if no performance data exists
  3. Calculate which tier was achieved (1, 2, or 3)
  4. Update `tier_achieved` field automatically
- Added proper logging to track what's being calculated

**Files Changed:**
- `backend/src/main/java/com/ayurveda/platform/tenant/service/SalespersonTargetService.java`

**What Changed:**
```java
// BEFORE: Only checked orders, would return 0 if no matches
BigDecimal totalSales = orderRepository.findBySalespersonIdAndOrderDateBetween(...)

// AFTER: Checks performance data first, falls back to orders
BigDecimal totalSales = performanceRepository.sumSalesBySalespersonAndDateRange(...);
if (totalSales == null || totalSales.compareTo(BigDecimal.ZERO) == 0) {
    totalSales = orderRepository.findBySalespersonIdAndOrderDateBetween(...);
}

// ALSO ADDED: Automatic tier calculation
Integer tierAchieved = 0;
if (totalSales >= targetTier1) tierAchieved = 1;
if (totalSales >= targetTier2) tierAchieved = 2;
if (totalSales >= targetTier3) tierAchieved = 3;
```

### 3. Deprecated Methods Fixed ✅

**Problem:** Using deprecated BigDecimal methods.

**Fixed:**
- Changed `BigDecimal.ROUND_HALF_UP` to `RoundingMode.HALF_UP`
- Updated division method to use modern API

## How to Restore Data if Already Cleared

If you already ran recalculate and lost your data, run this:

```bash
# In MySQL Workbench or command line
mysql -u root -proot@123 shifa_db < FIX_TARGETS_DATA.sql
```

Or open `FIX_TARGETS_DATA.sql` in MySQL Workbench and execute it.

## Testing the Fix

### 1. Restart Backend
```bash
cd backend
mvn spring-boot:run
```

### 2. Test Recalculate (Admin/Sales Head Only)

**Method 1: Using Postman**
```
POST http://localhost:8080/api/salesperson/targets/recalculate?month=6&year=2026
Authorization: Bearer {admin_token}
```

**Method 2: Using curl**
```bash
# Login as admin first
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# Copy the token, then:
curl -X POST "http://localhost:8080/api/salesperson/targets/recalculate?month=6&year=2026" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 3. Verify in Database

```sql
-- Check that achieved amounts are correctly calculated
SELECT 
    s.employee_code,
    s.name,
    st.achieved_amount,
    st.tier_achieved,
    CASE 
        WHEN st.tier_achieved = 3 THEN 'Tier 3 🏆'
        WHEN st.tier_achieved = 2 THEN 'Tier 2 🥈'
        WHEN st.tier_achieved = 1 THEN 'Tier 1 🥉'
        ELSE 'Below Target'
    END as status
FROM salesperson_targets st
JOIN salespersons s ON st.salesperson_user_id = s.id
WHERE st.month = 6 AND st.year = 2026
ORDER BY st.achieved_amount DESC;
```

Expected results after recalculate:
- SP004 (Vikram): ₹112,700 - Tier 3 🏆
- SP001 (Priya): ₹104,100 - Tier 2 🥈
- SP002 (Amit): ₹83,500 - Tier 1 🥉
- SP003 (Neha): ₹69,500 - Tier 1 🥉
- SP005 (Anita): ₹52,800 - Below Target

## What Now Works

✅ **Recalculate button** properly calculates achievements from performance data
✅ **Automatic tier detection** - Sets tier_achieved field (0, 1, 2, or 3)
✅ **Fallback to orders** - If performance data is missing, uses orders table
✅ **SQL seed data** - Runs without syntax errors
✅ **No data loss** - Recalculate now updates correctly instead of clearing

## Important Notes

### When to Use Recalculate

Use the recalculate endpoint when:
- Performance data has been updated manually
- Orders have been backdated or modified
- You want to ensure targets reflect the latest sales data
- You've imported historical data

### Auto-Update vs Manual Recalculate

- **Auto-update**: When new orders are created with a salesperson assigned, the achievement SHOULD be automatically updated (implement this in order creation service)
- **Manual recalculate**: Use the endpoint to force-refresh all targets for a month

### Future Enhancement Needed

Add automatic achievement updates when orders are created/updated:

```java
// In OrderService, after creating an order with a salesperson:
if (order.getSalespersonId() != null) {
    YearMonth yearMonth = YearMonth.from(order.getOrderDate());
    salespersonTargetService.updateAchievement(
        order.getSalespersonId(), 
        yearMonth.getMonthValue(),
        yearMonth.getYear(),
        order.getTotalAmount()
    );
}
```

## Deployment

Once tested locally:

```bash
# Commit all changes
git add .
git commit -m "fix: Recalculate targets bug and SQL syntax errors"
git push

# Railway will auto-deploy
# Then run the seed SQL on Aiven cloud database
```

## Files Modified

1. `SALES_TEAM_SEED_DATA.sql` - SQL syntax fixes
2. `TESTING_INSTRUCTIONS.md` - Updated queries
3. `SalespersonTargetService.java` - Recalculate logic fix
4. `FIX_TARGETS_DATA.sql` - Created (data restore script)
5. `FIXES_APPLIED.md` - Created (this file)
