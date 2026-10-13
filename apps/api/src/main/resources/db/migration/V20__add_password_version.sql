-- Existing sessions stay at version zero until the first credential revocation.
ALTER TABLE users ADD COLUMN password_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE refresh_tokens ADD COLUMN password_version INTEGER NOT NULL DEFAULT 0;
