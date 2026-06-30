-- Quick fix to restore target data after recalculate cleared it
-- Run this if you accidentally ran recalculate and lost data

USE shifa_db;

-- Get salesperson IDs
SET @sp001_id = (SELECT id FROM salespersons WHERE employee_code = 'SP001');
SET @sp002_id = (SELECT id FROM salespersons WHERE employee_code = 'SP002');
SET @sp003_id = (SELECT id FROM salespersons WHERE employee_code = 'SP003');
SET @sp004_id = (SELECT id FROM salespersons WHERE employee_code = 'SP004');
SET @sp005_id = (SELECT id FROM salespersons WHERE employee_code = 'SP005');

-- Restore June 2026 targets
UPDATE salesperson_targets 
SET achieved_amount = 48500.00, tier_achieved = 2 
WHERE salesperson_user_id = @sp001_id AND month = 6 AND year = 2026;

UPDATE salesperson_targets 
SET achieved_amount = 42000.00, tier_achieved = 1 
WHERE salesperson_user_id = @sp002_id AND month = 6 AND year = 2026;

UPDATE salesperson_targets 
SET achieved_amount = 38000.00, tier_achieved = 1 
WHERE salesperson_user_id = @sp003_id AND month = 6 AND year = 2026;

UPDATE salesperson_targets 
SET achieved_amount = 56000.00, tier_achieved = 3 
WHERE salesperson_user_id = @sp004_id AND month = 6 AND year = 2026;

UPDATE salesperson_targets 
SET achieved_amount = 22000.00, tier_achieved = 0 
WHERE salesperson_user_id = @sp005_id AND month = 6 AND year = 2026;

-- Verify
SELECT 
    s.employee_code,
    s.name,
    st.month,
    st.year,
    st.target_tier1,
    st.target_tier2,
    st.target_tier3,
    st.achieved_amount,
    st.tier_achieved,
    CASE 
        WHEN st.tier_achieved = 3 THEN 'Tier 3 Achieved! 🏆'
        WHEN st.tier_achieved = 2 THEN 'Tier 2 Achieved! 🥈'
        WHEN st.tier_achieved = 1 THEN 'Tier 1 Achieved! 🥉'
        ELSE 'Below Target'
    END as status
FROM salesperson_targets st
JOIN salespersons s ON st.salesperson_user_id = s.id
WHERE st.month = 6 AND st.year = 2026
ORDER BY st.achieved_amount DESC;
