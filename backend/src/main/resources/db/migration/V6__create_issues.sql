-- Per-project counter behind issue keys such as ECOM-12 (incremented atomically when an issue is created).
ALTER TABLE projects ADD COLUMN issue_counter INTEGER NOT NULL DEFAULT 0;

CREATE TABLE issues (
    id                     UUID                     PRIMARY KEY,
    project_id             UUID                     NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    issue_number           INTEGER                  NOT NULL,
    type                   VARCHAR(16)              NOT NULL,
    title                  VARCHAR(255)             NOT NULL,
    description            VARCHAR(10000),
    parent_id              UUID                     REFERENCES issues (id),
    priority               VARCHAR(16)              NOT NULL,
    status                 VARCHAR(16)              NOT NULL,
    refinement             VARCHAR(16)              NOT NULL,
    reporter_id            UUID                     NOT NULL REFERENCES users (id),
    assignee_id            UUID                     REFERENCES users (id),
    start_date             DATE,
    due_date               DATE,
    story_points           INTEGER,
    original_estimate_min  INTEGER,
    remaining_estimate_min INTEGER,
    acceptance_criteria    VARCHAR(10000),
    -- Order in the product backlog: ascending, with gaps so a move rewrites one row.
    backlog_rank           BIGINT                   NOT NULL,
    -- First time the issue entered work / the time it reached DONE (cycle and lead time).
    started_at             TIMESTAMP WITH TIME ZONE,
    done_at                TIMESTAMP WITH TIME ZONE,
    -- Soft delete: history (burndown, velocity) must survive removal.
    deleted_at             TIMESTAMP WITH TIME ZONE,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_issues_project_number ON issues (project_id, issue_number);
CREATE INDEX ix_issues_project_rank ON issues (project_id, backlog_rank);
CREATE INDEX ix_issues_project_status ON issues (project_id, status);
CREATE INDEX ix_issues_assignee ON issues (assignee_id);
CREATE INDEX ix_issues_parent ON issues (parent_id);

CREATE TABLE issue_labels (
    issue_id UUID        NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    label    VARCHAR(30) NOT NULL,
    PRIMARY KEY (issue_id, label)
);

CREATE TABLE issue_checklist_items (
    id         UUID                     PRIMARY KEY,
    issue_id   UUID                     NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    text       VARCHAR(500)             NOT NULL,
    done       BOOLEAN                  NOT NULL,
    position   INTEGER                  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_checklist_issue ON issue_checklist_items (issue_id, position);

CREATE TABLE issue_comments (
    id         UUID                     PRIMARY KEY,
    issue_id   UUID                     NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    author_id  UUID                     NOT NULL REFERENCES users (id),
    body       VARCHAR(5000)            NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_comments_issue ON issue_comments (issue_id, created_at);

-- Append-only change history. For status/story_points/assignee the values are what burndown and flow
-- metrics replay; for long text fields old/new are left empty (only the fact of the change is recorded).
CREATE TABLE issue_activity (
    id         UUID                     PRIMARY KEY,
    issue_id   UUID                     NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    actor_id   UUID                     NOT NULL REFERENCES users (id),
    kind       VARCHAR(16)              NOT NULL,
    field      VARCHAR(32),
    old_value  VARCHAR(500),
    new_value  VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_activity_issue ON issue_activity (issue_id, created_at);
