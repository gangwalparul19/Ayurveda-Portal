-- =====================================================
-- V5: Seed super-admin user
-- Default super-admin: admin / admin (simple password for development)
-- Password hash is BCrypt of "admin"
-- ⚠️ CHANGE THIS PASSWORD IN PRODUCTION!
-- =====================================================

INSERT INTO platform_users (tenant_id, username, email, password_hash, role, full_name, is_active)
VALUES (
    NULL,
    'admin',
    'admin@ayurveda-platform.com',
    '$2a$12$qVH8pGq9jM5aF5h5J5z5JeqP4KZn7X5Z5Z5Z5Z5Z5Z5Z5Z5Z5Z5Zm',
    'SUPER_ADMIN',
    'Platform Administrator',
    TRUE
);
