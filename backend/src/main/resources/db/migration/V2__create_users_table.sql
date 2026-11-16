
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,

    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,

    username VARCHAR(80) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Prevent duplicate email addresses regardless of letter casing.
CREATE UNIQUE INDEX uq_users_email_lower
ON users (LOWER(email));

-- Remove the temporary table created during initial database testing.
DROP TABLE IF EXISTS schema_test;
