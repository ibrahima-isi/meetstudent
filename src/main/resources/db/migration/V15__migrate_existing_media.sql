-- Migrate existing private personal documents (diploma/certificate string arrays)
-- into the media table. Physical files are relocated by MediaMigrationRunner at startup;
-- storage_key here keeps the legacy relative path until the runner rewrites it.

-- Diplomas: users.diplomas is a text[] of relative paths (e.g. 'users/uuid.pdf')
INSERT INTO media (storage_key, original_filename, content_type, category, visibility, owner_id, verification_status, created_at, modified_at)
SELECT d AS storage_key,
       d AS original_filename,
       NULL AS content_type,
       'DIPLOMA' AS category,
       'PRIVATE' AS visibility,
       u.id AS owner_id,
       'PENDING' AS verification_status,
       now() AS created_at,
       now() AS modified_at
FROM users u, unnest(u.diplomas) AS d
WHERE u.diplomas IS NOT NULL;

-- Certificates
INSERT INTO media (storage_key, original_filename, content_type, category, visibility, owner_id, verification_status, created_at, modified_at)
SELECT c AS storage_key,
       c AS original_filename,
       NULL AS content_type,
       'CERTIFICATE' AS category,
       'PRIVATE' AS visibility,
       u.id AS owner_id,
       'PENDING' AS verification_status,
       now() AS created_at,
       now() AS modified_at
FROM users u, unnest(u.certificates) AS c
WHERE u.certificates IS NOT NULL;

-- Presentation videos (private, single value per user)
INSERT INTO media (storage_key, original_filename, content_type, category, visibility, owner_id, verification_status, created_at, modified_at)
SELECT u.presentation_video_url AS storage_key,
       u.presentation_video_url AS original_filename,
       NULL AS content_type,
       'PRESENTATION_VIDEO' AS category,
       'PRIVATE' AS visibility,
       u.id AS owner_id,
       'PENDING' AS verification_status,
       now() AS created_at,
       now() AS modified_at
FROM users u
WHERE u.presentation_video_url IS NOT NULL AND u.presentation_video_url <> '';

-- Drop the legacy media columns now that data lives in the media table.
ALTER TABLE users DROP COLUMN IF EXISTS diplomas;
ALTER TABLE users DROP COLUMN IF EXISTS certificates;
ALTER TABLE users DROP COLUMN IF EXISTS presentation_video_url;
