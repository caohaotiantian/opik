--liquibase formatted sql
--changeset multi-tenant:000036_add_project_members_table
--comment: Create project_members table to manage project-level membership and permissions

CREATE TABLE project_members (
    id CHAR(36) NOT NULL COMMENT 'Membership ID (UUID)',
    project_id CHAR(36) NOT NULL COMMENT 'Project ID',
    user_id CHAR(36) NOT NULL COMMENT 'User ID',
    role_id CHAR(36) NOT NULL COMMENT 'Role ID',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'Membership status: active, suspended',
    added_by CHAR(36) COMMENT 'User ID who added this member',
    added_at TIMESTAMP(6) COMMENT 'Timestamp when member was added to project',
    version INT NOT NULL DEFAULT 0 COMMENT 'Version number for optimistic locking',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation timestamp',
    created_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT 'Creator',
    last_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update timestamp',
    last_updated_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT 'Last updater',
    CONSTRAINT project_members_pk PRIMARY KEY (id),
    CONSTRAINT project_members_project_user_uk UNIQUE (project_id, user_id),
    CONSTRAINT project_members_status_check CHECK (status IN ('active', 'suspended')),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_members_role FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Project members table for managing project-level permissions';

-- Index for project membership lookups (most frequent query)
CREATE INDEX idx_project_members_project ON project_members(project_id);

-- Index for user membership lookups (find all projects for a user)
CREATE INDEX idx_project_members_user ON project_members(user_id);

-- Index for role queries (permission checks)
CREATE INDEX idx_project_members_role ON project_members(role_id);

-- Composite index for project + user + status (permission validation)
CREATE INDEX idx_project_members_lookup ON project_members(project_id, user_id, status);

--rollback DROP INDEX IF EXISTS idx_project_members_lookup ON project_members;
--rollback DROP INDEX IF EXISTS idx_project_members_role ON project_members;
--rollback DROP INDEX IF EXISTS idx_project_members_user ON project_members;
--rollback DROP INDEX IF EXISTS idx_project_members_project ON project_members;
--rollback DROP TABLE IF EXISTS project_members;


