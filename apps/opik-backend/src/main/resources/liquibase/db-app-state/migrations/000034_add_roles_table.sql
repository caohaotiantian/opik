--liquibase formatted sql
--changeset multi-tenant:000034_add_roles_table
--comment: Create roles table with built-in roles for RBAC permission system

CREATE TABLE roles (
    id CHAR(36) NOT NULL COMMENT 'Role ID (UUID)',
    name VARCHAR(100) NOT NULL COMMENT 'Role name',
    scope VARCHAR(20) NOT NULL COMMENT 'Role scope: system, workspace, project',
    description TEXT COMMENT 'Role description',
    permissions JSON NOT NULL COMMENT 'Permission list (JSON array)',
    is_builtin BOOLEAN DEFAULT FALSE COMMENT 'Whether this is a built-in role (cannot be deleted)',
    workspace_id CHAR(36) COMMENT 'Workspace ID for custom roles',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation timestamp',
    created_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT 'Creator',
    last_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update timestamp',
    last_updated_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT 'Last updater',
    CONSTRAINT roles_pk PRIMARY KEY (id),
    CONSTRAINT roles_scope_check CHECK (scope IN ('system', 'workspace', 'project'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Roles table for RBAC permission system';

-- Index for scope queries
CREATE INDEX idx_roles_scope ON roles(scope);

-- Index for workspace lookups
CREATE INDEX idx_roles_workspace ON roles(workspace_id);

-- Index for built-in role queries
CREATE INDEX idx_roles_builtin ON roles(is_builtin);

-- Insert built-in roles

-- 1. System Admin (System Level)
INSERT INTO roles (id, name, scope, description, permissions, is_builtin) VALUES (
    '10000000-0000-0000-0000-000000000001',
    'System Admin',
    'system',
    'System administrator with all permissions across the entire system',
    JSON_ARRAY(
        'SYSTEM_ADMIN',
        'SYSTEM_USER_MANAGE',
        'SYSTEM_WORKSPACE_MANAGE',
        'SYSTEM_SETTINGS',
        'SYSTEM_AUDIT_VIEW'
    ),
    TRUE
);

-- 2. Workspace Admin (Workspace Level)
INSERT INTO roles (id, name, scope, description, permissions, is_builtin) VALUES (
    '10000000-0000-0000-0000-000000000002',
    'Workspace Admin',
    'workspace',
    'Workspace administrator with full permissions within workspace',
    JSON_ARRAY(
        'WORKSPACE_ADMIN',
        'WORKSPACE_VIEW',
        'WORKSPACE_SETTINGS',
        'WORKSPACE_MEMBER_MANAGE',
        'PROJECT_CREATE', 'PROJECT_VIEW', 'PROJECT_UPDATE', 'PROJECT_DELETE',
        'TRACE_CREATE', 'TRACE_VIEW', 'TRACE_UPDATE', 'TRACE_DELETE',
        'DATASET_CREATE', 'DATASET_VIEW', 'DATASET_UPDATE', 'DATASET_DELETE',
        'PROMPT_CREATE', 'PROMPT_VIEW', 'PROMPT_UPDATE', 'PROMPT_DELETE',
        'EXPERIMENT_CREATE', 'EXPERIMENT_VIEW', 'EXPERIMENT_UPDATE', 'EXPERIMENT_DELETE',
        'API_KEY_CREATE', 'API_KEY_VIEW', 'API_KEY_REVOKE',
        'FEEDBACK_DEFINITION_CREATE', 'FEEDBACK_DEFINITION_VIEW',
        'FEEDBACK_DEFINITION_UPDATE', 'FEEDBACK_DEFINITION_DELETE'
    ),
    TRUE
);

-- 3. Developer (Workspace Level)
INSERT INTO roles (id, name, scope, description, permissions, is_builtin) VALUES (
    '10000000-0000-0000-0000-000000000003',
    'Developer',
    'workspace',
    'Developer with create and edit permissions for most resources',
    JSON_ARRAY(
        'WORKSPACE_VIEW',
        'PROJECT_CREATE', 'PROJECT_VIEW', 'PROJECT_UPDATE', 'PROJECT_DELETE',
        'TRACE_CREATE', 'TRACE_VIEW', 'TRACE_UPDATE', 'TRACE_DELETE',
        'DATASET_CREATE', 'DATASET_VIEW', 'DATASET_UPDATE', 'DATASET_DELETE',
        'PROMPT_CREATE', 'PROMPT_VIEW', 'PROMPT_UPDATE', 'PROMPT_DELETE',
        'EXPERIMENT_CREATE', 'EXPERIMENT_VIEW', 'EXPERIMENT_UPDATE', 'EXPERIMENT_DELETE',
        'API_KEY_CREATE', 'API_KEY_VIEW',
        'FEEDBACK_DEFINITION_VIEW'
    ),
    TRUE
);

-- 4. Viewer (Workspace Level)
INSERT INTO roles (id, name, scope, description, permissions, is_builtin) VALUES (
    '10000000-0000-0000-0000-000000000004',
    'Viewer',
    'workspace',
    'Read-only access to workspace resources',
    JSON_ARRAY(
        'WORKSPACE_VIEW',
        'PROJECT_VIEW',
        'TRACE_VIEW',
        'DATASET_VIEW',
        'PROMPT_VIEW',
        'EXPERIMENT_VIEW',
        'FEEDBACK_DEFINITION_VIEW'
    ),
    TRUE
);

-- 5. Project Admin (Project Level)
INSERT INTO roles (id, name, scope, description, permissions, is_builtin) VALUES (
    '10000000-0000-0000-0000-000000000005',
    'Project Admin',
    'project',
    'Project administrator with full permissions within project',
    JSON_ARRAY(
        'PROJECT_VIEW', 'PROJECT_UPDATE', 'PROJECT_DELETE',
        'TRACE_CREATE', 'TRACE_VIEW', 'TRACE_UPDATE', 'TRACE_DELETE'
    ),
    TRUE
);

-- 6. Project Contributor (Project Level)
INSERT INTO roles (id, name, scope, description, permissions, is_builtin) VALUES (
    '10000000-0000-0000-0000-000000000006',
    'Project Contributor',
    'project',
    'Project contributor with create and edit permissions for traces',
    JSON_ARRAY(
        'PROJECT_VIEW',
        'TRACE_CREATE', 'TRACE_VIEW', 'TRACE_UPDATE'
    ),
    TRUE
);

-- 7. Project Viewer (Project Level)
INSERT INTO roles (id, name, scope, description, permissions, is_builtin) VALUES (
    '10000000-0000-0000-0000-000000000007',
    'Project Viewer',
    'project',
    'Read-only access to project and traces',
    JSON_ARRAY(
        'PROJECT_VIEW',
        'TRACE_VIEW'
    ),
    TRUE
);

--rollback DROP INDEX IF EXISTS idx_roles_builtin ON roles;
--rollback DROP INDEX IF EXISTS idx_roles_workspace ON roles;
--rollback DROP INDEX IF EXISTS idx_roles_scope ON roles;
--rollback DELETE FROM roles WHERE id IN ('10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000007');
--rollback DROP TABLE IF EXISTS roles;


