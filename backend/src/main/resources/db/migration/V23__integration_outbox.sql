-- Durable "this project changed" notifications for integrations (ForgeBridge). One undelivered row per project is
-- enough: the receiver reads the current state, so several changes coalesce.
CREATE TABLE integration_outbox (
    id              UUID PRIMARY KEY,
    project_id      UUID NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts        INT NOT NULL DEFAULT 0,
    delivered_at    TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_integration_outbox_pending ON integration_outbox (delivered_at, next_attempt_at);
CREATE INDEX idx_integration_outbox_project ON integration_outbox (project_id, delivered_at);
