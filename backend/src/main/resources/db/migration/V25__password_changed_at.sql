-- When the password last changed. Compared with the same value in SmartSpec so a password changed in one system
-- is adopted by the other, while a stale one is never accepted again.
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMP WITH TIME ZONE;
UPDATE users SET password_changed_at = created_at;
