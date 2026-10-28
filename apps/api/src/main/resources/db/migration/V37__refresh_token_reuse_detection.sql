-- Refresh-token reuse detection: each session keeps the SHA-256 of the secret its last rotation replaced and when
-- that happened. Presenting that previous secret again (after a short grace window for concurrent refreshes) means
-- the token was copied, and the whole session is ended. Existing sessions start with neither value: nothing to
-- backfill, nobody is signed out by the deployment.
ALTER TABLE refresh_tokens ADD COLUMN previous_token varchar(255);
ALTER TABLE refresh_tokens ADD COLUMN rotated_at timestamp with time zone;
CREATE INDEX refresh_tokens_previous_token_idx ON refresh_tokens (previous_token);
