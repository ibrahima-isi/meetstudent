-- Optional program details: all nullable, no invented default.
ALTER TABLE programs ADD COLUMN description VARCHAR(1000);
ALTER TABLE programs ADD COLUMN available_seats INTEGER;
ALTER TABLE programs ADD COLUMN annual_fee NUMERIC(10, 2);
ALTER TABLE programs ADD COLUMN level VARCHAR(20);

ALTER TABLE programs ADD CONSTRAINT ck_programs_available_seats CHECK (available_seats IS NULL OR available_seats >= 0);
ALTER TABLE programs ADD CONSTRAINT ck_programs_annual_fee CHECK (annual_fee IS NULL OR annual_fee >= 0);
ALTER TABLE programs ADD CONSTRAINT ck_programs_level CHECK (level IS NULL OR level IN ('BTS_DUT', 'LICENCE', 'MASTER', 'DOCTORAT', 'AUTRE'));
