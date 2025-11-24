--liquibase formatted sql
--changeset multi-tenant:000033_add_workspaces_table
--comment: Create workspaces table to support multi-workspace multi-tenant system

CREATE TABLE workspaces (
    id CHAR(36) NOT NULL COMMENT 'Workspace ID (UUID)',
    name VARCHAR(150) NOT NULL COMMENT 'Workspace name (unique, used in URLs)',
    display_name VARCHAR(255) NOT NULL COMMENT 'Workspace display name',
    description TEXT COMMENT 'Workspace description',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'Workspace status: active, suspended, deleted',
    owner_user_id CHAR(36) NOT NULL COMMENT 'Owner user ID',
    quota_limit INT DEFAULT 10 COMMENT 'Quota limit for workspace',
    allow_public_access BOOLEAN DEFAULT FALSE COMMENT 'Whether to allow public access',
    max_members INT DEFAULT 100 COMMENT 'Maximum number of members',
    settings JSON COMMENT 'Additional settings in JSON format',
    version INT NOT NULL DEFAULT 0 COMMENT 'Version number for optimistic locking',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation timestamp',
    created_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT 'Creator',
    last_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update timestamp',
    last_updated_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT 'Last updater',
    CONSTRAINT workspaces_pk PRIMARY KEY (id),
    CONSTRAINT workspaces_name_uk UNIQUE (name),
    CONSTRAINT workspaces_status_check CHECK (status IN ('active', 'suspended', 'deleted'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workspaces table for multi-tenant support';

-- Index for workspace status filtering
CREATE INDEX idx_workspaces_status ON workspaces(status);

-- Index for owner lookups
CREATE INDEX idx_workspaces_owner ON workspaces(owner_user_id);

-- Index for name lookups
CREATE INDEX idx_workspaces_name ON workspaces(name);

-- Create default workspace (backwards compatibility with existing data)
-- Using the same workspace ID as the current default: 0190babc-62a0-71d2-832a-0feffa4676eb
INSERT INTO workspaces (
    id, 
    name, 
    display_name, 
    description, 
    owner_user_id, 
    status,
    quota_limit
) VALUES (
    '0190babc-62a0-71d2-832a-0feffa4676eb',
    'default',
    'Default Workspace',
    'The default workspace for backward compatibility. All existing projects, datasets, and resources are associated with this workspace.',
    '00000000-0000-0000-0000-000000000001',
    'active',
    10
);

--rollback DROP INDEX IF EXISTS idx_workspaces_name ON workspaces;
--rollback DROP INDEX IF EXISTS idx_workspaces_owner ON workspaces;
--rollback DROP INDEX IF EXISTS idx_workspaces_status ON workspaces;
--rollback DELETE FROM workspaces WHERE id = '0190babc-62a0-71d2-832a-0feffa4676eb';
--rollback DROP TABLE IF EXISTS workspaces;


