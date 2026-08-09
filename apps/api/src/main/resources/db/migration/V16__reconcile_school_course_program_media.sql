-- Reconcile school/course/program image fields with the media table.
-- Replaces URL-string columns with nullable FKs into media(id).

ALTER TABLE schools  ADD COLUMN logo_media_id  INTEGER;
ALTER TABLE schools  ADD COLUMN cover_media_id INTEGER;
ALTER TABLE courses  ADD COLUMN photo_media_id INTEGER;
ALTER TABLE programs ADD COLUMN photo_media_id INTEGER;

ALTER TABLE schools
    ADD CONSTRAINT fk_schools_logo_media
        FOREIGN KEY (logo_media_id) REFERENCES media (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_schools_cover_media
        FOREIGN KEY (cover_media_id) REFERENCES media (id) ON DELETE SET NULL;

ALTER TABLE courses
    ADD CONSTRAINT fk_courses_photo_media
        FOREIGN KEY (photo_media_id) REFERENCES media (id) ON DELETE SET NULL;

ALTER TABLE programs
    ADD CONSTRAINT fk_programs_photo_media
        FOREIGN KEY (photo_media_id) REFERENCES media (id) ON DELETE SET NULL;

-- Legacy URL values are intentionally discarded (no data migration, per design).
ALTER TABLE schools  DROP COLUMN logo_url;
ALTER TABLE schools  DROP COLUMN cover_photo_url;
ALTER TABLE courses  DROP COLUMN photo_url;
ALTER TABLE programs DROP COLUMN photo_url;
