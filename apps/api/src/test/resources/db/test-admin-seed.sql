-- Test-only stand-in for V2__data.sql (Flyway is disabled in the H2 test suite).
-- Seeds the ROLE_ADMIN role and the default admin carrying the bcrypt hash of 'password'.
INSERT INTO roles (name, description) VALUES ('ROLE_ADMIN', 'Administrator with full access');
INSERT INTO users (firstname, lastname, email, password, role_id, created_at, modified_at)
VALUES ('System', 'Admin', 'admin@meetstudent.com',
        '$2a$10$upwmHP5SvZQCBWozT9IVLeFWXo5MUE8J15P02YVVevyGlt90UGE.m',
        (SELECT id FROM roles WHERE name = 'ROLE_ADMIN'), NOW(), NOW());
