-- A direct conversation is between two people, wherever they work: it no longer belongs to a workspace.
ALTER TABLE conversations ALTER COLUMN workspace_id DROP NOT NULL;
UPDATE conversations SET workspace_id = NULL WHERE kind = 'DIRECT';
