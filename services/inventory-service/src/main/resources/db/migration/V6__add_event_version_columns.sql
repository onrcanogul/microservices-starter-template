-- Add version column to outbox and inbox tables for event schema versioning.
-- Default 1 ensures backward compatibility with existing rows.

ALTER TABLE outbox ADD COLUMN version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE inbox ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
