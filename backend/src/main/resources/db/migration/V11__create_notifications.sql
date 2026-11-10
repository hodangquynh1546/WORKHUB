-- In-app notifications, per-user channel preferences and per-project progress-alert thresholds.
CREATE TABLE notifications (
    id         UUID                     PRIMARY KEY,
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(40)              NOT NULL,
    title      VARCHAR(300)             NOT NULL,
    body       VARCHAR(1000),
    -- Frontend route the notification opens, e.g. /issues/{id}.
    link       VARCHAR(300),
    project_id UUID                     REFERENCES projects (id) ON DELETE CASCADE,
    actor_id   UUID                     REFERENCES users (id) ON DELETE SET NULL,
    -- Alerts are re-evaluated on a schedule; one (user, dedupe_key) is delivered once. NULL = no deduplication.
    dedupe_key VARCHAR(200),
    read_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_notifications_user ON notifications (user_id, created_at);
CREATE UNIQUE INDEX uk_notifications_dedupe ON notifications (user_id, dedupe_key);

-- Only overrides are stored; a missing row means the type's defaults apply.
CREATE TABLE notification_preferences (
    id      UUID        PRIMARY KEY,
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type    VARCHAR(40) NOT NULL,
    in_app  BOOLEAN     NOT NULL,
    email   BOOLEAN     NOT NULL
);

CREATE UNIQUE INDEX uk_notification_preferences ON notification_preferences (user_id, type);

-- Thresholds of the progress alerts; a project without a row uses the defaults in code. 0 switches a rule off.
CREATE TABLE project_alert_settings (
    project_id            UUID    PRIMARY KEY REFERENCES projects (id) ON DELETE CASCADE,
    overdue_enabled       BOOLEAN NOT NULL,
    due_soon_days         INTEGER NOT NULL,
    sprint_risk_days_left INTEGER NOT NULL,
    sprint_risk_open_pct  INTEGER NOT NULL,
    blocked_enabled       BOOLEAN NOT NULL,
    overload_open_issues  INTEGER NOT NULL,
    scope_change_pct      INTEGER NOT NULL,
    no_progress_days      INTEGER NOT NULL
);
