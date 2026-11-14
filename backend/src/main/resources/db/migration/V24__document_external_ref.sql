-- Documents created and kept up to date by another system (e.g. "smartspec:design/<project>/<key>") are read-only for people.
ALTER TABLE documents ADD COLUMN external_ref VARCHAR(200);
CREATE INDEX idx_documents_external_ref ON documents (external_ref);
