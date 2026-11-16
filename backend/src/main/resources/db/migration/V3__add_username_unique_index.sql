-- Prevent usernames from being registered more than once,
-- regardless of uppercase or lowercase letters.
CREATE UNIQUE INDEX uq_users_username_lower
ON users (LOWER(username));