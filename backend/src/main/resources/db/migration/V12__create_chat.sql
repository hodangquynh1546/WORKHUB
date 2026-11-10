-- Chat: project and team channels (membership follows the project/team), direct messages and ad-hoc groups.
CREATE TABLE conversations (
    id              UUID                     PRIMARY KEY,
    kind            VARCHAR(16)              NOT NULL,
    workspace_id    UUID                     NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    project_id      UUID                     REFERENCES projects (id) ON DELETE CASCADE,
    team_id         UUID                     REFERENCES teams (id) ON DELETE CASCADE,
    title           VARCHAR(100),
    -- "<smaller user id>:<larger user id>" so a pair of people has exactly one direct conversation.
    direct_key      VARCHAR(80),
    created_by      UUID                     REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_message_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uk_conversations_project ON conversations (project_id);
CREATE UNIQUE INDEX uk_conversations_team ON conversations (team_id);
CREATE UNIQUE INDEX uk_conversations_direct ON conversations (direct_key);

-- Only for DIRECT and GROUP conversations.
CREATE TABLE conversation_members (
    id              UUID                     PRIMARY KEY,
    conversation_id UUID                     NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id         UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_conversation_members ON conversation_members (conversation_id, user_id);
CREATE INDEX ix_conversation_members_user ON conversation_members (user_id);

CREATE TABLE chat_messages (
    id              UUID                     PRIMARY KEY,
    conversation_id UUID                     NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id       UUID                     NOT NULL REFERENCES users (id),
    body            VARCHAR(4000)            NOT NULL,
    reply_to_id     UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    edited_at       TIMESTAMP WITH TIME ZONE,
    deleted_at      TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_chat_messages_conversation ON chat_messages (conversation_id, created_at);

CREATE TABLE message_reactions (
    id         UUID                     PRIMARY KEY,
    message_id UUID                     NOT NULL REFERENCES chat_messages (id) ON DELETE CASCADE,
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    emoji      VARCHAR(16)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_message_reactions ON message_reactions (message_id, user_id, emoji);

CREATE TABLE message_mentions (
    id         UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES chat_messages (id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_message_mentions ON message_mentions (message_id, user_id);

-- Everything in the conversation up to last_read_at counts as read for that user.
CREATE TABLE conversation_reads (
    id              UUID                     PRIMARY KEY,
    conversation_id UUID                     NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id         UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    last_read_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_conversation_reads ON conversation_reads (conversation_id, user_id);
