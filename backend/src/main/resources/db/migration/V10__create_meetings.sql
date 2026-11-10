-- Meetings (scheduling, agenda, minutes), attendees with their reply, and action items that can become issues.
CREATE TABLE meetings (
    id               UUID                     PRIMARY KEY,
    project_id       UUID                     NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    sprint_id        UUID                     REFERENCES sprints (id) ON DELETE SET NULL,
    title            VARCHAR(200)             NOT NULL,
    meeting_type     VARCHAR(32)              NOT NULL,
    organizer_id     UUID                     NOT NULL REFERENCES users (id),
    starts_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    location         VARCHAR(255),
    online_link      VARCHAR(500),
    agenda           TEXT,
    description      TEXT,
    minutes          TEXT,
    -- Bumped on every change of the minutes; a save based on an older one is refused (two people taking notes).
    minutes_revision INTEGER                  NOT NULL,
    -- Minutes before the start a reminder should go out. Stored now; sending arrives with notifications.
    reminder_minutes INTEGER,
    status           VARCHAR(16)              NOT NULL,
    created_by       UUID                     NOT NULL REFERENCES users (id),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at       TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_meetings_project_start ON meetings (project_id, starts_at);

CREATE TABLE meeting_attendees (
    id         UUID        PRIMARY KEY,
    meeting_id UUID        NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id),
    response   VARCHAR(16) NOT NULL
);

CREATE UNIQUE INDEX uk_meeting_attendees ON meeting_attendees (meeting_id, user_id);
CREATE INDEX ix_meeting_attendees_user ON meeting_attendees (user_id);

CREATE TABLE meeting_action_items (
    id          UUID                     PRIMARY KEY,
    meeting_id  UUID                     NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    item_text   VARCHAR(500)             NOT NULL,
    assignee_id UUID                     REFERENCES users (id),
    due_date    DATE,
    status      VARCHAR(16)              NOT NULL,
    -- Set once the item was turned into an issue; the issue is not a hard reference so it can be deleted.
    issue_id    UUID,
    created_by  UUID                     NOT NULL REFERENCES users (id),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_meeting_action_items_meeting ON meeting_action_items (meeting_id);
