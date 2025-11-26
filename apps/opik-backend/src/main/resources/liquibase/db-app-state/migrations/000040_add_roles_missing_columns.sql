--liquibase formatted sql
--changeset multi-tenant:000040_add_roles_missing_columns
--comment: Add missing display_name and version columns to roles table for consistency with Java record model

-- Add display_name column for user-friendly role names
ALTER TABLE roles ADD COLUMN display_name VARCHAR(255) COMMENT 'Display name for the role (user-friendly)';

-- Add version column for optimistic locking
ALTER TABLE roles ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT 'Version number for optimistic locking';

-- Update existing built-in roles with display names
UPDATE roles SET display_name = name WHERE display_name IS NULL;

-- Update existing built-in roles with initial version
UPDATE roles SET version = 0 WHERE version IS NULL;

-- Add index for display name lookups (used in UI)
CREATE INDEX idx_roles_display_name ON roles(display_name);

--rollback DROP INDEX IF EXISTS idx_roles_display_name ON roles;
--rollback ALTER TABLE roles DROP COLUMN version;
--rollback ALTER TABLE roles DROP COLUMN display_name;



