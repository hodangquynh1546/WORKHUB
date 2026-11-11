-- Someone invited to a team or a project (not to the workspace) is a guest of its workspace: the membership row is
-- needed for access checks, but the workspace is not theirs to browse or list.
ALTER TABLE workspace_members ADD COLUMN guest BOOLEAN NOT NULL DEFAULT FALSE;
