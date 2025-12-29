-- Insert initial roles with descriptions
INSERT INTO roles (id, name, description) VALUES (1, 'ROLE_ADMIN', 'Administrator with full access');
INSERT INTO roles (id, name, description) VALUES (2, 'ROLE_USER', 'Standard user with basic access');
INSERT INTO roles (id, name, description) VALUES (3, 'ROLE_EXPERT', 'Expert user capable of rating content');
INSERT INTO roles (id, name, description) VALUES (4, 'ROLE_STUDENT', 'Student user accessing educational content');

-- Insert Default Admin User
-- Password is 'password' (bcrypt hash)
INSERT INTO users (firstname, lastname, email, password, role_id, created_at, modified_at)
VALUES ('System', 'Admin', 'admin@meetstudent.com', '$2a$10$upwmHP5SvZQCBWozT9IVLeFWXo5MUE8J15P02YVVevyGlt90UGE.m', 1, NOW(), NOW());
