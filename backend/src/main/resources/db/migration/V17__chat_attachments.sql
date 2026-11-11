-- One optional file or image per chat message, stored in the database (like document attachments).
CREATE TABLE chat_attachments (
    id           UUID                     PRIMARY KEY,
    message_id   UUID                     NOT NULL REFERENCES chat_messages (id) ON DELETE CASCADE,
    file_name    VARCHAR(255)             NOT NULL,
    content_type VARCHAR(100)             NOT NULL,
    size_bytes   BIGINT                   NOT NULL,
    data         BYTEA                    NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_chat_attachments_message ON chat_attachments (message_id);
