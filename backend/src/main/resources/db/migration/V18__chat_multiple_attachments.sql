-- A message can carry several files: the one-per-message unique index goes, files keep their order in `position`.
DROP INDEX uk_chat_attachments_message;
ALTER TABLE chat_attachments ADD COLUMN position INT NOT NULL DEFAULT 0;
CREATE INDEX ix_chat_attachments_message ON chat_attachments (message_id, position);
