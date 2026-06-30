# Sales Team Performance Tracking Feature

## Overview

Comprehensive sales performance tracking system with:
- **3 Roles**: Admin, Sales Head (Manager), Salesperson
- **Personal Dashboards**: Daily, weekly, and monthly sales metrics
- **3-Tier Target System**: Basic (Tier 1), Mid (Tier 2), Stretch (Tier 3)
- **Leaderboards**: Weekly, monthly, and quarterly rankings
- **Awards**: Gold 🥇, Silver 🥈, Bronze 🥉 medals for top performers

## Features

### For Salespersons
- **Personal Dashboard** showing:
  - Today's sales, orders, and items sold
  - Week-to-date performance
  - Month-to-date performance
  - Commission earned
  - Target achievement progress (3 tiers)
  - 7-day performance trend chart
  
- **Leaderboards**:
  - See your rank among all salespersons
  - Weekly, monthly, quarterly views
  - Awards for top 3 performers
  - Encouragement to push for next tier

- **Target Tracking**:
  - Visual progress bars for each tier
  - Real-time achievement percentage
  - Tier status indicators

### For Sales Head / Admin
- View all salesperson dashboards
- Set monthly targets (3 tiers)
- View leaderboards
- Track team performance
- Recalculate achievements

## Database Schema

### New Tables

1. **salesperson_performance**
   - Tracks daily sales metrics per salesperson
   - Fields: date, orders_count, total_sales, items_sold, commission_earned
   - Used for leaderboards and analytics

2. **salesperson_targets** (Enhanced)
   - Added 3-tier target system
   - Fields: target_tier1, target_tier2, target_tier3, tier_achieved
   - Tracks monthly goals and achievement levels

## API Endpoints

### Salesperson Dashboard APIs

```
GET /api/salesperson-dashboard/{salespersonId}
- Get complete dashboard with all metrics

GET /api/salesperson-dashboard/{salespersonId}/today
- Today's performance only

GET /api/salesperson-dashboard/{salespersonId}/week
- Current week performance

GET /api/salesperson-dashboard/{salespersonId}/month
- Current month performance

GET /api/salesperson-dashboard/{salespersonId}/target-achievement
- Target progress with tier status

GET /api/salesperson-dashboard/{salespersonId}/trend
- Last 7 days performance chart data
```

### Leaderboard APIs

```
GET /api/salesperson-dashboard/leaderboard/week
- Weekly rankings (current week Monday-Sunday)

GET /api/salesperson-dashboard/leaderboard/month
- Monthly rankings (current month)

GET /api/salesperson-dashboard/leaderboard/quarter
- Quarterly rankings (current quarter)

GET /api/salesperson-dashboard/leaderboard/custom?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD
- Custom date range leaderboard

GET /api/salesperson-dashboard/{salespersonId}/my-rank/{period}
- Get my rank in week/month/quarter
```

### Admin/Sales Head APIs

```
POST /api/salesperson/targets
- Set monthly target with 3 tiers
Body: {
  "salespersonId": 1,
  "month": 6,
  "year": 2026,
  "targetAmount": 50000,  // Kept for backward compatibility
  "targetTier1": 40000,   // Basic target
  "targetTier2": 50000,   // Mid target
  "targetTier3": 60000    // Stretch target
}

GET /api/salesperson/targets?salespersonId=1&month=6&year=2026
- Get target for specific month

POST /api/salesperson/targets/recalculate?month=6&year=2026
- Recalculate all achievements for a month
```

## Setup Instructions

### 1. Run the Seed Data

```bash
# Connect to your local MySQL
mysql -u root -p shifa_db < SALES_TEAM_SEED_DATA.sql
```

This creates:
- 1 Sales Head user
- 5 Salespersons with different performance levels
- Monthly targets (May & June 2026) with 3 tiers
- Performance data for current week and historical data
- Sample leaderboard data

### 2. Start Backend

```bash
cd backend
mvn spring-boot:run
```

The backend will auto-create the new tables (`salesperson_performance`) on startup.

### 3. Test Login Credentials

| Role | Username | Password | Purpose |
|------|----------|----------|---------|
| Admin | admin | admin123 | Full system access |
| Sales Head | saleshead | Sales@123 | View all salespersons |
| Salesperson 1 | sp001 | Sales@123 | Top performer - Tier 2 achieved |
| Salesperson 2 | sp002 | Sales@123 | Mid performer - Tier 1 achieved |
| Salesperson 3 | sp003 | Sales@123 | Consistent - Tier 1 achieved |
| Salesperson 4 | sp004 | Sales@123 | Star - Tier 3 achieved! |
| Salesperson 5 | sp005 | Sales@123 | New - building up |

### 4. Test the APIs

Using Postman or curl:

#### Login as salesperson
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"sp001","password":"Sales@123"}'
```

#### Get Dashboard (use the token from login)
```bash
curl -X GET http://localhost:8080/api/salesperson-dashboard/1 \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

#### Get Weekly Leaderboard
```bash
curl -X GET http://localhost:8080/api/salesperson-dashboard/leaderboard/week \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

## Sample Data Overview

### Salespersons Created

1. **Priya Sharma (SP001)** - Top performer
   - Commission: 2.5%
   - Current month sales: ₹48,500
   - Status: Tier 2 Achieved 🥈

2. **Amit Patel (SP002)** - Mid performer
   - Commission: 2.5%
   - Current month sales: ₹42,000
   - Status: Tier 1 Achieved 🥉

3. **Neha Singh (SP003)** - Consistent
   - Commission: 2.0%
   - Current month sales: ₹38,000
   - Status: Tier 1 Achieved 🥉

4. **Vikram Reddy (SP004)** - Star performer
   - Commission: 2.0%
   - Current month sales: ₹56,000
   - Status: Tier 3 Achieved! 🏆

5. **Anita Desai (SP005)** - New member
   - Commission: 1.5%
   - Current month sales: ₹22,000
   - Status: Below Target

### June 2026 Leaderboard (Projected)

| Rank | Name | Sales | Orders | Award |
|------|------|-------|--------|-------|
| 1 | Vikram Reddy | ₹112,700 | 77 | 🥇 Gold |
| 2 | Priya Sharma | ₹104,100 | 70 | 🥈 Silver |
| 3 | Amit Patel | ₹83,500 | 56 | 🥉 Bronze |
| 4 | Neha Singh | ₹69,500 | 56 | - |
| 5 | Anita Desai | ₹52,800 | 43 | - |

## Next Steps for Production

1. **Test locally** with the seed data
2. **Create frontend UI** for salesperson dashboard
3. **Add charts/graphs** for performance visualization
4. **Push to GitHub** when ready
5. **Deploy to Railway** (backend auto-updates)
6. **Deploy to Vercel** (frontend)
7. **Run seed SQL on Aiven** cloud database

## Frontend TODO

Create Angular components for:
- Salesperson login page
- Personal dashboard with cards showing:
  - Today/Week/Month stats
  - Target achievement progress bars (3 tiers)
  - Performance trend chart
- Leaderboard page with:
  - Weekly/Monthly/Quarterly tabs
  - Rankings table with medals
  - "My Rank" highlight
- Admin target setting interface

## Troubleshooting

### If tables don't auto-create:
Run this manually:
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

ALTER TABLE salesperson_targets 
ADD COLUMN target_tier1 DECIMAL(12,2) DEFAULT 0.00,
ADD COLUMN target_tier2 DECIMAL(12,2) DEFAULT 0.00,
ADD COLUMN target_tier3 DECIMAL(12,2) DEFAULT 0.00,
ADD COLUMN tier_achieved INT;
```

### If seed data fails:
Check that platform_users and salespersons tables exist first.
Run the main SEED_DEMO_DATA.sql if needed before this one.

## Support

For questions or issues, check the logs:
```bash
# Backend logs
cd backend
mvn spring-boot:run

# Check MySQL
mysql -u root -p shifa_db
SELECT * FROM salespersons;
SELECT * FROM salesperson_performance;
```
