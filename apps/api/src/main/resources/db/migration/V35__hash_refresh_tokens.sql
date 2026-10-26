-- Refresh tokens are stored as their SHA-256 in lowercase hex, like user_tokens (V17): a database leak no longer
-- hands out usable sessions. Existing rows are hashed in place, so nobody is signed out by the deployment.
UPDATE refresh_tokens SET token = encode(sha256(convert_to(token, 'UTF8')), 'hex');
