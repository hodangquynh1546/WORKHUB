CREATE TABLE sprints (
    id               UUID                     PRIMARY KEY,
    project_id       UUID                     NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    name             VARCHAR(100)             NOT NULL,
    goal             VARCHAR(1000),
    start_date       DATE                     NOT NULL,
    end_date         DATE                     NOT NULL,
    status           VARCHAR(16)              NOT NULL,
    facilitator_id   UUID                     REFERENCES users (id),
    -- Planned capacity in story points, to compare against what the team commits to.
    capacity_points  INTEGER,
    -- Frozen when the sprint starts: the commitment burndown and reports measure against.
    committed_points INTEGER,
    committed_issues INTEGER,
    -- Set when the sprint completes (only DONE issues count).
    completed_points INTEGER,
    completed_issues INTEGER,
    goal_outcome     VARCHAR(24),
    goal_note        VARCHAR(1000),
    started_at       TIMESTAMP WITH TIME ZONE,
    completed_at     TIMESTAMP WITH TIME ZONE,
    created_by       UUID                     NOT NULL REFERENCES users (id),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_sprints_project_status ON sprints (project_id, status);

-- The open sprint (FUTURE or ACTIVE) an issue currently belongs to; for issues finished in a completed sprint it keeps
-- pointing at that sprint. Membership history lives in sprint_issues.
ALTER TABLE issues ADD COLUMN sprint_id UUID;
ALTER TABLE issues ADD CONSTRAINT fk_issues_sprint FOREIGN KEY (sprint_id) REFERENCES sprints (id);
CREATE INDEX ix_issues_sprint ON issues (sprint_id);

ALTER TABLE issues ADD COLUMN blocked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE issues ADD COLUMN blocked_reason VARCHAR(500);

-- One row per stretch of an issue's time in a sprint: what the scope was and how it changed. Rows are never deleted.
CREATE TABLE sprint_issues (
    id                  UUID                     PRIMARY KEY,
    sprint_id           UUID                     NOT NULL REFERENCES sprints (id) ON DELETE CASCADE,
    issue_id            UUID                     NOT NULL REFERENCES issues (id),
    added_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    added_by            UUID                     NOT NULL REFERENCES users (id),
    added_points        INTEGER,
    -- True when the issue entered after the sprint had started (scope creep).
    added_after_start   BOOLEAN                  NOT NULL,
    removed_at          TIMESTAMP WITH TIME ZONE,
    removed_by          UUID                     REFERENCES users (id),
    removal_reason      VARCHAR(16),
    removed_points      INTEGER,
    -- Outcome recorded when the sprint completes.
    final_status        VARCHAR(16),
    final_points        INTEGER
);

CREATE INDEX ix_sprint_issues_sprint ON sprint_issues (sprint_id);
CREATE INDEX ix_sprint_issues_issue ON sprint_issues (issue_id, removed_at);

-- End-of-day state of an active sprint (one row per sprint and day, rewritten as the day goes on): the series the
-- burndown, burnup and cumulative flow charts are drawn from.
CREATE TABLE sprint_snapshots (
    id                 UUID                     PRIMARY KEY,
    sprint_id          UUID                     NOT NULL REFERENCES sprints (id) ON DELETE CASCADE,
    snapshot_date      DATE                     NOT NULL,
    scope_points       INTEGER                  NOT NULL,
    done_points        INTEGER                  NOT NULL,
    scope_issues       INTEGER                  NOT NULL,
    todo_issues        INTEGER                  NOT NULL,
    in_progress_issues INTEGER                  NOT NULL,
    done_issues        INTEGER                  NOT NULL,
    captured_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_sprint_snapshots_day ON sprint_snapshots (sprint_id, snapshot_date);

-- Per-project display name and visibility of the five fixed statuses (rows appear once a project customises them).
CREATE TABLE workflow_statuses (
    project_id UUID        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    status     VARCHAR(16) NOT NULL,
    name       VARCHAR(40) NOT NULL,
    enabled    BOOLEAN     NOT NULL,
    PRIMARY KEY (project_id, status)
);
