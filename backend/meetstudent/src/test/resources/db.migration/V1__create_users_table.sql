CREATE TABLE IF NOT EXISTS users (
     id INTEGER NOT NULL,
     user_type SMALLINT CHECK (user_type BETWEEN 0 AND 2),
    user_role VARCHAR(31) NOT NULL,
    lastname VARCHAR(50),
    firstname VARCHAR(100),
    diploma VARCHAR(255),
    email VARCHAR(255) UNIQUE,
    password VARCHAR(255),
    PRIMARY KEY (id)
    );