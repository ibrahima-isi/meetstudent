-- One rating per user per target. Existing duplicates are removed first, keeping the most
-- recent rating of each (user, target) pair (latest created_at, then highest id).
DELETE FROM school_rates r USING school_rates newer
WHERE r.user_id = newer.user_id AND r.school_id = newer.school_id
  AND (COALESCE(newer.created_at, TIMESTAMP '-infinity'), newer.id) > (COALESCE(r.created_at, TIMESTAMP '-infinity'), r.id);
DELETE FROM program_rates r USING program_rates newer
WHERE r.user_id = newer.user_id AND r.program_id = newer.program_id
  AND (COALESCE(newer.created_at, TIMESTAMP '-infinity'), newer.id) > (COALESCE(r.created_at, TIMESTAMP '-infinity'), r.id);
DELETE FROM course_rates r USING course_rates newer
WHERE r.user_id = newer.user_id AND r.course_id = newer.course_id
  AND (COALESCE(newer.created_at, TIMESTAMP '-infinity'), newer.id) > (COALESCE(r.created_at, TIMESTAMP '-infinity'), r.id);

ALTER TABLE school_rates ADD CONSTRAINT uk_school_rates_user_school UNIQUE (user_id, school_id);
ALTER TABLE program_rates ADD CONSTRAINT uk_program_rates_user_program UNIQUE (user_id, program_id);
ALTER TABLE course_rates ADD CONSTRAINT uk_course_rates_user_course UNIQUE (user_id, course_id);
