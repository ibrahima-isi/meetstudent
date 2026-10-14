-- A refresh row is now an independent device session. Rotate only its secret, never its identity.
ALTER TABLE refresh_tokens ADD COLUMN session_id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE refresh_tokens ADD COLUMN last_activity timestamp with time zone;
ALTER TABLE refresh_tokens ADD COLUMN legacy boolean NOT NULL DEFAULT false;
ALTER TABLE refresh_tokens ADD CONSTRAINT refresh_tokens_session_id_key UNIQUE (session_id);
CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens(user_id);

-- Valid historical sessions receive a seven-day deployment grace period.
-- Already-expired credentials stay expired; migration must not revive them.
UPDATE refresh_tokens SET last_activity = CASE
    WHEN expiry_date > CURRENT_TIMESTAMP THEN CURRENT_TIMESTAMP
    ELSE expiry_date - INTERVAL '7 days' END;

-- Historical JWTs have no device identity. The latest live row represents that
-- unavoidable shared compatibility session until clients rotate to a sid JWT.
UPDATE refresh_tokens r SET legacy = true
WHERE r.id IN (
    SELECT DISTINCT ON (r2.user_id) r2.id FROM refresh_tokens r2
    JOIN users u ON u.id = r2.user_id AND u.password_version = r2.password_version
    WHERE r2.expiry_date > CURRENT_TIMESTAMP ORDER BY r2.user_id, r2.id DESC
);

-- Old access JWTs may still be valid even when their old refresh row was deleted
-- by another login. Give those accounts the same bounded compatibility period.
INSERT INTO refresh_tokens (token, expiry_date, user_id, password_version, last_activity, legacy)
SELECT gen_random_uuid()::text, CURRENT_TIMESTAMP + INTERVAL '7 days', u.id,
       u.password_version, CURRENT_TIMESTAMP, true
FROM users u WHERE NOT EXISTS (
    SELECT 1 FROM refresh_tokens r WHERE r.user_id = u.id AND r.legacy
);
ALTER TABLE refresh_tokens ALTER COLUMN last_activity SET NOT NULL;
CREATE UNIQUE INDEX refresh_tokens_legacy_user_idx ON refresh_tokens(user_id) WHERE legacy;
