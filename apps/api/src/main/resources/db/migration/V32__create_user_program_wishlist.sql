CREATE TABLE IF NOT EXISTS user_program_wishlist (
    user_id integer NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    program_id integer NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, program_id)
);
CREATE INDEX IF NOT EXISTS idx_user_program_wishlist_program ON user_program_wishlist (program_id);
