-- Add logo_url to schools
ALTER TABLE schools ADD COLUMN logo_url VARCHAR(255);

-- Add photo_url to courses
ALTER TABLE courses ADD COLUMN photo_url VARCHAR(255);

-- Add photo_url to programs
ALTER TABLE programs ADD COLUMN photo_url VARCHAR(255);

-- Add photo_url to users
ALTER TABLE users ADD COLUMN photo_url VARCHAR(255);
