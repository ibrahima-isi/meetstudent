-- Remove code and name columns from school_rates
ALTER TABLE school_rates DROP COLUMN code;
ALTER TABLE school_rates DROP COLUMN name;

-- Remove code and name columns from program_rates
ALTER TABLE program_rates DROP COLUMN code;
ALTER TABLE program_rates DROP COLUMN name;

-- Remove code and name columns from course_rates
ALTER TABLE course_rates DROP COLUMN code;
ALTER TABLE course_rates DROP COLUMN name;
