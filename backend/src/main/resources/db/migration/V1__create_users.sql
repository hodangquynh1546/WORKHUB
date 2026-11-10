-- Must stay portable: runs on PostgreSQL and on H2 (PostgreSQL mode) used by the default profile and tests.
CREATE TABLE users (
    id             UUID                     PRIMARY KEY,
    email          VARCHAR(320)             NOT NULL,
    password_hash  VARCHAR(255)             NOT NULL,
    full_name      VARCHAR(150)             NOT NULL,
    avatar_url     VARCHAR(1024),
    email_verified BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Emails are normalised to lower case by the application before saving.
CREATE UNIQUE INDEX uk_users_email ON users (email);
