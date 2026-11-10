-- Uploaded profile pictures live in their own table so user lists never carry the image bytes;
-- users.avatar_url then points at /api/public/users/{id}/avatar?v=<version>.
CREATE TABLE user_avatars (
    user_id      UUID                     PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    content_type VARCHAR(50)              NOT NULL,
    data         BYTEA                    NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
