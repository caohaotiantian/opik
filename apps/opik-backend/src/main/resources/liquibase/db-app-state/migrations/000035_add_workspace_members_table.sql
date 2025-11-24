--liquibase formatted sql
--changeset multi-tenant:000035_add_workspace_members_table
--comment: Create workspace_members table to manage workspace membership and permissions

CREATE TABLE workspace_members (
    id CHAR(36) NOT NULL COMMENT 'Membership ID (UUID)',
    workspace_id CHAR(36) NOT NULL COMMENT 'Workspace ID',
    user_id CHAR(36) NOT NULL COMMENT 'User ID',
    role_id CHAR(36) NOT NULL COMMENT 'Role ID',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'Membership status: active, suspended',
    invited_by CHAR(36) COMMENT 'Inviter user ID',
    joined_at TIMESTAMP(6) COMMENT 'Timestamp when user joined workspace',
    version INT NOT NULL DEFAULT 0 COMMENT 'Version number for optimistic locking',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation timestamp',
    created_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT 'Creator',
    last_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update timestamp',
    last_updated_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT 'Last updater',
    CONSTRAINT workspace_members_pk PRIMARY KEY (id),
    CONSTRAINT workspace_members_workspace_user_uk UNIQUE (workspace_id, user_id),
    CONSTRAINT workspace_members_status_check CHECK (status IN ('active', 'suspended')),
    CONSTRAINT fk_workspace_members_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    CONSTRAINT fk_workspace_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_workspace_members_role FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workspace members table for managing workspace-level permissions';

-- Index for workspace membership lookups (most frequent query)
CREATE INDEX idx_workspace_members_workspace ON workspace_members(workspace_id);

-- Index for user membership lookups (find all workspaces for a user)
CREATE INDEX idx_workspace_members_user ON workspace_members(user_id);

-- Index for role queries (permission checks)
CREATE INDEX idx_workspace_members_role ON workspace_members(role_id);

-- Composite index for workspace + user + status (permission validation)
CREATE INDEX idx_workspace_members_lookup ON workspace_members(workspace_id, user_id, status);

-- Insert default admin membership for default workspace
INSERT INTO workspace_members (
    id,
    workspace_id,
    user_id,
    role_id,
    status,
    joined_at
) VALUES (
    '20000000-0000-0000-0000-000000000001',
    '0190babc-62a0-71d2-832a-0feffa4676eb',
    '00000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    'active',
    CURRENT_TIMESTAMP(6)
);

--rollback DROP INDEX IF EXISTS idx_workspace_members_lookup ON workspace_members;
--rollback DROP INDEX IF EXISTS idx_workspace_members_role ON workspace_members;
--rollback DROP INDEX IF EXISTS idx_workspace_members_user ON workspace_members;
--rollback DROP INDEX IF EXISTS idx_workspace_members_workspace ON workspace_members;
--rollback DELETE FROM workspace_members WHERE id = '20000000-0000-0000-0000-000000000001';
--rollback DROP TABLE IF EXISTS workspace_members;


