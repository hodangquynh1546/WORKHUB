-- Integration tokens: long-lived, revocable credentials that an external system (ForgeBridge, VS Code) exchanges
-- for a normal short-lived access token. Only the SHA-256 hash is stored.
CREATE TABLE integration_tokens (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    revoked_at   TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_integration_tokens_user ON integration_tokens (user_id);

-- Where an issue came from in another system, e.g. "smartspec:usecase/<id>".
ALTER TABLE issues ADD COLUMN external_ref VARCHAR(200);
CREATE INDEX idx_issues_external_ref ON issues (external_ref);
