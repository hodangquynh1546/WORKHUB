CREATE TABLE workspaces (
    id          UUID                     PRIMARY KEY,
    name        VARCHAR(100)             NOT NULL,
    description VARCHAR(1000),
    logo_url    VARCHAR(1024),
    created_by  UUID                     NOT NULL REFERENCES users (id),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE workspace_members (
    id           UUID                     PRIMARY KEY,
    workspace_id UUID                     NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id      UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role         VARCHAR(32)              NOT NULL,
    joined_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_workspace_members ON workspace_members (workspace_id, user_id);
CREATE INDEX ix_workspace_members_user ON workspace_members (user_id);
