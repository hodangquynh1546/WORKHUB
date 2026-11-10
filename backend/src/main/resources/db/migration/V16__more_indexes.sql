-- Foreign keys that queries filter or join on but that had no index of their own (found with a pg_constraint /
-- pg_index comparison on PostgreSQL). Audit-style columns (created_by, actor_id...) are left out on purpose.
CREATE INDEX ix_conversation_reads_user ON conversation_reads (user_id);
CREATE INDEX ix_document_folders_parent ON document_folders (parent_id);
CREATE INDEX ix_meetings_organizer ON meetings (organizer_id);
CREATE INDEX ix_meeting_action_items_assignee ON meeting_action_items (assignee_id);
CREATE INDEX ix_message_mentions_user ON message_mentions (user_id);
CREATE INDEX ix_conversations_workspace ON conversations (workspace_id);
CREATE INDEX ix_invitations_team ON invitations (team_id);
