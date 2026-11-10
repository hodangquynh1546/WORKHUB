-- One-time and refresh tokens. Only the SHA-256 hash of a token is stored, never the token itself.
-- consumed_at: the token was redeemed (a refresh token was rotated). Presenting it again signals theft.
-- revoked_at:  the token was invalidated (logout, password change, superseded). Presenting it is just rejected.
CREATE TABLE user_tokens (
    id          UUID                     PRIMARY KEY,
    user_id     UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(32)              NOT NULL,
    token_hash  VARCHAR(64)              NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    revoked_at  TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_user_tokens_hash ON user_tokens (token_hash);
CREATE INDEX ix_user_tokens_user_type ON user_tokens (user_id, type);
