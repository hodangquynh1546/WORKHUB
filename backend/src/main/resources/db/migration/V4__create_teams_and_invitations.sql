CREATE TABLE teams (
    id           UUID                     PRIMARY KEY,
    workspace_id UUID                     NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name         VARCHAR(100)             NOT NULL,
    description  VARCHAR(1000),
    archived_at  TIMESTAMP WITH TIME ZONE,
    created_by   UUID                     NOT NULL REFERENCES users (id),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Name uniqueness per workspace is case-insensitive and enforced by TeamService
-- (H2 has no expression indexes, so it cannot be a portable constraint).
CREATE INDEX ix_teams_workspace ON teams (workspace_id);

CREATE TABLE team_members (
    id        UUID                     PRIMARY KEY,
    team_id   UUID                     NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    user_id   UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role      VARCHAR(32)              NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_team_members ON team_members (team_id, user_id);
CREATE INDEX ix_team_members_user ON team_members (user_id);

-- Invitation into a workspace (team_id NULL; role is a workspace role) or into a team (role is a team role;
-- accepting also makes the user a workspace MEMBER). kind EMAIL: single-use, for one address.
-- kind LINK: email NULL, reusable until it expires or is revoked.
CREATE TABLE invitations (
    id           UUID                     PRIMARY KEY,
    workspace_id UUID                     NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    team_id      UUID                     REFERENCES teams (id) ON DELETE CASCADE,
    kind         VARCHAR(16)              NOT NULL,
    email        VARCHAR(320),
    role         VARCHAR(32)              NOT NULL,
    token_hash   VARCHAR(64)              NOT NULL,
    status       VARCHAR(16)              NOT NULL,
    invited_by   UUID                     NOT NULL REFERENCES users (id),
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at TIMESTAMP WITH TIME ZONE,
    responded_by UUID                     REFERENCES users (id),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_invitations_token ON invitations (token_hash);
CREATE INDEX ix_invitations_workspace_status ON invitations (workspace_id, status);
CREATE INDEX ix_invitations_email_status ON invitations (email, status);
