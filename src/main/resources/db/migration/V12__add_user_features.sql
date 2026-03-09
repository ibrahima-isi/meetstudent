ALTER TABLE users ADD COLUMN IF NOT EXISTS certificates varchar(255) array;
ALTER TABLE users ADD COLUMN IF NOT EXISTS presentation_video_url varchar(255);

CREATE TABLE IF NOT EXISTS user_wishlist (
    user_id integer NOT NULL REFERENCES users(id),
    school_id integer NOT NULL REFERENCES schools(id),
    PRIMARY KEY (user_id, school_id)
);
