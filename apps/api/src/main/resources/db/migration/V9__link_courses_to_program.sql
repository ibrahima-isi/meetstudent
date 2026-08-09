ALTER TABLE courses
ADD COLUMN program_id INTEGER;

ALTER TABLE courses
ADD CONSTRAINT fk_courses_program_id
FOREIGN KEY (program_id) REFERENCES programs(id) ON DELETE CASCADE;
