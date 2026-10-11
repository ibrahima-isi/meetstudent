-- Supports the per-address cap, which counts tokens by lower(email) across all users.
CREATE INDEX idx_user_tokens_lower_email_issued ON user_tokens (lower(email), issued_at);
