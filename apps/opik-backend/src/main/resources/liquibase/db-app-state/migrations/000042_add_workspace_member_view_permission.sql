--liquibase formatted sql
--changeset multi-tenant:000042_add_workspace_member_view_permission
--comment: Add WORKSPACE_MEMBER_VIEW permission to roles that need to view workspace members

-- Add WORKSPACE_MEMBER_VIEW to Workspace Admin role
UPDATE roles
SET permissions = JSON_ARRAY_APPEND(permissions, '$', 'WORKSPACE_MEMBER_VIEW'),
    last_updated_at = CURRENT_TIMESTAMP(6),
    last_updated_by = 'migration'
WHERE id = '10000000-0000-0000-0000-000000000002'
  AND name = 'Workspace Admin';

-- Add WORKSPACE_MEMBER_VIEW to Developer role
UPDATE roles
SET permissions = JSON_ARRAY_APPEND(permissions, '$', 'WORKSPACE_MEMBER_VIEW'),
    last_updated_at = CURRENT_TIMESTAMP(6),
    last_updated_by = 'migration'
WHERE id = '10000000-0000-0000-0000-000000000003'
  AND name = 'Developer';

-- Add WORKSPACE_MEMBER_VIEW to Viewer role
UPDATE roles
SET permissions = JSON_ARRAY_APPEND(permissions, '$', 'WORKSPACE_MEMBER_VIEW'),
    last_updated_at = CURRENT_TIMESTAMP(6),
    last_updated_by = 'migration'
WHERE id = '10000000-0000-0000-0000-000000000004'
  AND name = 'Viewer';

--rollback UPDATE roles SET permissions = JSON_REMOVE(permissions, JSON_UNQUOTE(JSON_SEARCH(permissions, 'one', 'WORKSPACE_MEMBER_VIEW'))) WHERE id IN ('10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004');


