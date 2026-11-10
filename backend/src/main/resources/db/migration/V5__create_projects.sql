CREATE TABLE projects (
    id           UUID                     PRIMARY KEY,
    workspace_id UUID                     NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    team_id      UUID                     NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    -- Short uppercase code (e.g. ECOM), prefix of future issue keys; immutable after creation.
    project_key  VARCHAR(10)              NOT NULL,
    name         VARCHAR(100)             NOT NULL,
    description  VARCHAR(2000),
    avatar_url   VARCHAR(1024),
    start_date   DATE,
    end_date     DATE,
    methodology  VARCHAR(16)              NOT NULL,
    status       VARCHAR(16)              NOT NULL,
    created_by   UUID                     NOT NULL REFERENCES users (id),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_projects_workspace_key ON projects (workspace_id, project_key);
CREATE INDEX ix_projects_team ON projects (team_id);

CREATE TABLE project_members (
    id         UUID                     PRIMARY KEY,
    project_id UUID                     NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       VARCHAR(32)              NOT NULL,
    joined_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_project_members ON project_members (project_id, user_id);
CREATE INDEX ix_project_members_user ON project_members (user_id);
