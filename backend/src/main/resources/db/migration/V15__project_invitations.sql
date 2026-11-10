-- An invitation can also be into one project: accepting adds the person to the workspace (as MEMBER, if they are
-- not in it yet) and to the project with project_role. For these rows team_id stays NULL, so the person does not
-- join the project's team.
ALTER TABLE invitations ADD COLUMN project_id UUID REFERENCES projects (id) ON DELETE CASCADE;

CREATE INDEX ix_invitations_project_status ON invitations (project_id, status);
