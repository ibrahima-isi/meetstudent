-- A program rating without a program is meaningless (and the unique (user_id, program_id) of V24 does not constrain NULLs,
-- so the same user could post it again and again). Delete the orphan rows, then forbid them: back up before deploying.
DELETE FROM program_rates WHERE program_id IS NULL;
ALTER TABLE program_rates ALTER COLUMN program_id SET NOT NULL;
