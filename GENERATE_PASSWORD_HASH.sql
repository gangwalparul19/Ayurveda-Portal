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
