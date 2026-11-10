-- Append-only record of who did what (administrative and security-relevant actions). Rows are never updated or
-- deleted by the application. Actor and target names are copied in, so the log stays readable after renames/deletes.
CREATE TABLE audit_log (
    id           UUID                     PRIMARY KEY,
    -- NULL for actions outside any workspace (sign-up, sign-in, password change).
    workspace_id UUID                     REFERENCES workspaces (id) ON DELETE CASCADE,
    project_id   UUID,
    actor_id     UUID,
    actor_name   VARCHAR(150),
    action       VARCHAR(60)              NOT NULL,
    target_type  VARCHAR(30),
    target_id    UUID,
    target_label VARCHAR(300),
    detail       VARCHAR(500),
    outcome      VARCHAR(10)              NOT NULL,
    ip           VARCHAR(64),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_audit_workspace ON audit_log (workspace_id, created_at);
CREATE INDEX ix_audit_project ON audit_log (project_id, created_at);
CREATE INDEX ix_audit_actor ON audit_log (actor_id, created_at);
