-- A member can hold several project roles: one row per (project, user, role).
DROP INDEX uk_project_members;
CREATE UNIQUE INDEX uk_project_members ON project_members (project_id, user_id, role);
CREATE INDEX ix_project_members_project_user ON project_members (project_id, user_id);

-- Project pictures are uploaded files (like profile pictures), kept apart from the project row.
CREATE TABLE project_avatars (
    project_id   UUID                     PRIMARY KEY REFERENCES projects (id) ON DELETE CASCADE,
    content_type VARCHAR(100)             NOT NULL,
    data         BYTEA                    NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
