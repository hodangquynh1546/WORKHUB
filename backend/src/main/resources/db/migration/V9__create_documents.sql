-- Project documents (Markdown text), folders, immutable version history, file attachments and links to issues/sprints.
CREATE TABLE document_folders (
    id         UUID                     PRIMARY KEY,
    project_id UUID                     NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    parent_id  UUID                     REFERENCES document_folders (id) ON DELETE CASCADE,
    name       VARCHAR(100)             NOT NULL,
    created_by UUID                     NOT NULL REFERENCES users (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_document_folders_project ON document_folders (project_id);

CREATE TABLE documents (
    id             UUID                     PRIMARY KEY,
    project_id     UUID                     NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    folder_id      UUID                     REFERENCES document_folders (id) ON DELETE SET NULL,
    title          VARCHAR(200)             NOT NULL,
    doc_type       VARCHAR(40)              NOT NULL,
    content        TEXT                     NOT NULL,
    status         VARCHAR(16)              NOT NULL,
    -- Number of the newest row in document_versions.
    version_number INTEGER                  NOT NULL,
    -- Optimistic lock: two saves based on the same state cannot both win.
    lock_version   BIGINT                   NOT NULL,
    created_by     UUID                     NOT NULL REFERENCES users (id),
    updated_by     UUID                     NOT NULL REFERENCES users (id),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at     TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_documents_project ON documents (project_id, updated_at);
CREATE INDEX ix_documents_folder ON documents (folder_id);

-- Append-only: every change to the title or content, and every restore, adds a row holding the full text.
CREATE TABLE document_versions (
    id             UUID                     PRIMARY KEY,
    document_id    UUID                     NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    version_number INTEGER                  NOT NULL,
    title          VARCHAR(200)             NOT NULL,
    content        TEXT                     NOT NULL,
    note           VARCHAR(200),
    created_by     UUID                     NOT NULL REFERENCES users (id),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_document_versions ON document_versions (document_id, version_number);

CREATE TABLE document_attachments (
    id           UUID                     PRIMARY KEY,
    document_id  UUID                     NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    file_name    VARCHAR(255)             NOT NULL,
    content_type VARCHAR(100)             NOT NULL,
    size_bytes   BIGINT                   NOT NULL,
    data         BYTEA                    NOT NULL,
    uploaded_by  UUID                     NOT NULL REFERENCES users (id),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_document_attachments_document ON document_attachments (document_id);

-- target_type is ISSUE or SPRINT (MEETING arrives with the meetings module); target_id is not a foreign key
-- because it points at different tables.
CREATE TABLE document_links (
    id          UUID                     PRIMARY KEY,
    document_id UUID                     NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    target_type VARCHAR(16)              NOT NULL,
    target_id   UUID                     NOT NULL,
    created_by  UUID                     NOT NULL REFERENCES users (id),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_document_links ON document_links (document_id, target_type, target_id);
CREATE INDEX ix_document_links_target ON document_links (target_type, target_id);
