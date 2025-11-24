# 数据库设计

## 1. MySQL 表结构设计

### 1.1 用户表 (users)

```sql
--liquibase formatted sql
--changeset system:add_users_table
--comment: 创建用户表,支持多用户系统

CREATE TABLE users (
    id CHAR(36) NOT NULL COMMENT '用户ID (UUID)',
    username VARCHAR(100) NOT NULL COMMENT '用户名,用于登录',
    email VARCHAR(255) NOT NULL COMMENT '邮箱地址',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希 (BCrypt)',
    full_name VARCHAR(255) COMMENT '用户全名',
    avatar_url VARCHAR(500) COMMENT '头像URL',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '用户状态: active, suspended, deleted',
    is_system_admin BOOLEAN DEFAULT FALSE COMMENT '是否系统管理员',
    email_verified BOOLEAN DEFAULT FALSE COMMENT '邮箱是否已验证',
    last_login_at TIMESTAMP(6) COMMENT '最后登录时间',
    locale VARCHAR(10) DEFAULT 'en-US' COMMENT '用户语言偏好',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号,用于乐观锁',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    created_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT '创建人',
    last_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间',
    last_updated_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT '最后更新人',
    CONSTRAINT users_pk PRIMARY KEY (id),
    CONSTRAINT users_username_uk UNIQUE (username),
    CONSTRAINT users_email_uk UNIQUE (email),
    CONSTRAINT users_status_check CHECK (status IN ('active', 'suspended', 'deleted')),
    INDEX idx_users_status (status),
    INDEX idx_users_email (email),
    INDEX idx_users_system_admin (is_system_admin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 插入默认系统管理员 (密码: Admin@123)
INSERT INTO users (id, username, email, password_hash, full_name, is_system_admin, status)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin',
    'admin@opik.local',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYb8Fo0e5A6',  -- Admin@123
    'System Administrator',
    TRUE,
    'active'
);

--rollback DROP TABLE IF EXISTS users;
```

### 1.2 工作空间表 (workspaces)

```sql
--liquibase formatted sql
--changeset system:add_workspaces_table
--comment: 创建工作空间表

CREATE TABLE workspaces (
    id CHAR(36) NOT NULL COMMENT '工作空间ID (UUID)',
    name VARCHAR(150) NOT NULL COMMENT '工作空间名称 (唯一)',
    display_name VARCHAR(255) NOT NULL COMMENT '工作空间显示名称',
    description TEXT COMMENT '工作空间描述',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '工作空间状态: active, suspended, deleted',
    owner_user_id CHAR(36) NOT NULL COMMENT '所有者用户ID',
    quota_limit INT DEFAULT 10 COMMENT '配额限制',
    allow_public_access BOOLEAN DEFAULT FALSE COMMENT '是否允许公开访问',
    max_members INT DEFAULT 100 COMMENT '最大成员数',
    settings JSON COMMENT '其他配置 (JSON格式)',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号,用于乐观锁',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    created_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT '创建人',
    last_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间',
    last_updated_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT '最后更新人',
    CONSTRAINT workspaces_pk PRIMARY KEY (id),
    CONSTRAINT workspaces_name_uk UNIQUE (name),
    CONSTRAINT workspaces_status_check CHECK (status IN ('active', 'suspended', 'deleted')),
    -- 注意: 生产环境建议移除外键约束以提升性能，在应用层保证引用完整性
    -- CONSTRAINT workspaces_owner_fk FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_workspaces_status (status),
    INDEX idx_workspaces_owner (owner_user_id),
    INDEX idx_workspaces_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作空间表';

-- 创建默认工作空间并关联给系统管理员
INSERT INTO workspaces (id, name, display_name, description, owner_user_id, status)
VALUES (
    '0190babc-62a0-71d2-832a-0feffa4676eb',  -- 与现有 DEFAULT_WORKSPACE_ID 一致
    'default',
    'Default Workspace',
    'The default workspace for backward compatibility',
    '00000000-0000-0000-0000-000000000001',  -- admin user
    'active'
);

--rollback DROP TABLE IF EXISTS workspaces;
```

### 1.3 角色表 (roles)

```sql
--liquibase formatted sql
--changeset system:add_roles_table
--comment: 创建角色表,支持系统级和工作空间级角色

CREATE TABLE roles (
    id CHAR(36) NOT NULL COMMENT '角色ID (UUID)',
    name VARCHAR(100) NOT NULL COMMENT '角色名称',
    scope ENUM('system', 'workspace', 'project') NOT NULL COMMENT '角色作用域',
    description TEXT COMMENT '角色描述',
    permissions JSON NOT NULL COMMENT '权限列表 (JSON数组)',
    is_builtin BOOLEAN DEFAULT FALSE COMMENT '是否内置角色 (内置角色不可删除)',
    workspace_id CHAR(36) COMMENT '所属工作空间ID (自定义角色)',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    created_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT '创建人',
    last_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间',
    last_updated_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT '最后更新人',
    CONSTRAINT roles_pk PRIMARY KEY (id),
    CONSTRAINT roles_name_scope_workspace_uk UNIQUE (name, scope, workspace_id),
    CONSTRAINT roles_workspace_fk FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    INDEX idx_roles_scope (scope),
    INDEX idx_roles_workspace (workspace_id),
    INDEX idx_roles_builtin (is_builtin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 插入内置角色
INSERT INTO roles (id, name, scope, description, permissions, is_builtin) VALUES
-- 系统级角色
('10000000-0000-0000-0000-000000000001', 'System Admin', 'system', '系统管理员,拥有所有权限', 
 '["SYSTEM_ADMIN", "SYSTEM_USER_MANAGE", "SYSTEM_WORKSPACE_MANAGE", "SYSTEM_SETTINGS", "SYSTEM_AUDIT_VIEW"]', 
 TRUE),

-- 工作空间级角色
('10000000-0000-0000-0000-000000000002', 'Workspace Admin', 'workspace', '工作空间管理员',
 '["WORKSPACE_ADMIN", "WORKSPACE_VIEW", "WORKSPACE_SETTINGS", "WORKSPACE_MEMBER_MANAGE", "PROJECT_CREATE", "PROJECT_VIEW", "PROJECT_UPDATE", "PROJECT_DELETE", "TRACE_CREATE", "TRACE_VIEW", "TRACE_UPDATE", "TRACE_DELETE", "DATASET_CREATE", "DATASET_VIEW", "DATASET_UPDATE", "DATASET_DELETE", "PROMPT_CREATE", "PROMPT_VIEW", "PROMPT_UPDATE", "PROMPT_DELETE", "API_KEY_CREATE", "API_KEY_VIEW", "API_KEY_REVOKE"]',
 TRUE),

('10000000-0000-0000-0000-000000000003', 'Developer', 'workspace', '开发者',
 '["WORKSPACE_VIEW", "PROJECT_CREATE", "PROJECT_VIEW", "PROJECT_UPDATE", "PROJECT_DELETE", "TRACE_CREATE", "TRACE_VIEW", "TRACE_UPDATE", "TRACE_DELETE", "DATASET_CREATE", "DATASET_VIEW", "DATASET_UPDATE", "DATASET_DELETE", "PROMPT_CREATE", "PROMPT_VIEW", "PROMPT_UPDATE", "PROMPT_DELETE", "API_KEY_CREATE", "API_KEY_VIEW"]',
 TRUE),

('10000000-0000-0000-0000-000000000004', 'Viewer', 'workspace', '查看者',
 '["WORKSPACE_VIEW", "PROJECT_VIEW", "TRACE_VIEW", "DATASET_VIEW", "PROMPT_VIEW"]',
 TRUE),

-- 项目级角色
('10000000-0000-0000-0000-000000000005', 'Project Admin', 'project', '项目管理员',
 '["PROJECT_VIEW", "PROJECT_UPDATE", "PROJECT_DELETE", "TRACE_CREATE", "TRACE_VIEW", "TRACE_UPDATE", "TRACE_DELETE"]',
 TRUE),

('10000000-0000-0000-0000-000000000006', 'Project Contributor', 'project', '项目贡献者',
 '["PROJECT_VIEW", "TRACE_CREATE", "TRACE_VIEW", "TRACE_UPDATE"]',
 TRUE),

('10000000-0000-0000-0000-000000000007', 'Project Viewer', 'project', '项目查看者',
 '["PROJECT_VIEW", "TRACE_VIEW"]',
 TRUE);

--rollback DROP TABLE IF EXISTS roles;
```

### 1.4 工作空间成员表 (workspace_members)

```sql
--liquibase formatted sql
--changeset system:add_workspace_members_table
--comment: 创建工作空间成员表

CREATE TABLE workspace_members (
    id CHAR(36) NOT NULL COMMENT '成员记录ID (UUID)',
    workspace_id CHAR(36) NOT NULL COMMENT '工作空间ID',
    user_id CHAR(36) NOT NULL COMMENT '用户ID',
    role_id CHAR(36) NOT NULL COMMENT '角色ID',
    status ENUM('active', 'suspended', 'pending_invitation') DEFAULT 'active' COMMENT '成员状态',
    joined_at TIMESTAMP(6) COMMENT '加入时间',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    created_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT '创建人',
    last_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间',
    last_updated_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT '最后更新人',
    CONSTRAINT workspace_members_pk PRIMARY KEY (id),
    CONSTRAINT workspace_members_workspace_user_uk UNIQUE (workspace_id, user_id),
    CONSTRAINT workspace_members_workspace_fk FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    CONSTRAINT workspace_members_user_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT workspace_members_role_fk FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT,
    INDEX idx_workspace_members_workspace (workspace_id),
    INDEX idx_workspace_members_user (user_id),
    INDEX idx_workspace_members_status (status),
    INDEX idx_workspace_members_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作空间成员表';

-- 将 admin 用户添加到默认工作空间,角色为 Workspace Admin
INSERT INTO workspace_members (id, workspace_id, user_id, role_id, status, joined_at)
VALUES (
    '20000000-0000-0000-0000-000000000001',
    '0190babc-62a0-71d2-832a-0feffa4676eb',  -- default workspace
    '00000000-0000-0000-0000-000000000001',  -- admin user
    '10000000-0000-0000-0000-000000000002',  -- Workspace Admin role
    'active',
    CURRENT_TIMESTAMP(6)
);

--rollback DROP TABLE IF EXISTS workspace_members;
```

### 1.5 项目成员表 (project_members)

```sql
--liquibase formatted sql
--changeset system:add_project_members_table
--comment: 创建项目成员表,支持项目级权限控制

CREATE TABLE project_members (
    id CHAR(36) NOT NULL COMMENT '项目成员记录ID (UUID)',
    project_id CHAR(36) NOT NULL COMMENT '项目ID',
    user_id CHAR(36) NOT NULL COMMENT '用户ID',
    role_id CHAR(36) NOT NULL COMMENT '角色ID (项目级角色)',
    status ENUM('active', 'suspended') DEFAULT 'active' COMMENT '成员状态',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    created_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT '创建人',
    last_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间',
    last_updated_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT '最后更新人',
    CONSTRAINT project_members_pk PRIMARY KEY (id),
    CONSTRAINT project_members_project_user_uk UNIQUE (project_id, user_id),
    CONSTRAINT project_members_project_fk FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT project_members_user_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT project_members_role_fk FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT,
    INDEX idx_project_members_project (project_id),
    INDEX idx_project_members_user (user_id),
    INDEX idx_project_members_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目成员表';

--rollback DROP TABLE IF EXISTS project_members;
```

### 1.6 API Key 表 (user_api_keys)

```sql
--liquibase formatted sql
--changeset system:add_user_api_keys_table
--comment: 创建用户API Key表

CREATE TABLE user_api_keys (
    id CHAR(36) NOT NULL COMMENT 'API Key ID (UUID)',
    user_id CHAR(36) NOT NULL COMMENT '用户ID',
    workspace_id CHAR(36) NOT NULL COMMENT '工作空间ID',
    key_hash VARCHAR(255) NOT NULL COMMENT 'API Key 哈希值 (SHA-256)',
    key_prefix VARCHAR(20) NOT NULL COMMENT 'API Key 前缀 (用于显示)',
    name VARCHAR(255) NOT NULL COMMENT 'API Key 名称',
    description TEXT COMMENT 'API Key 描述',
    status ENUM('active', 'revoked', 'expired') DEFAULT 'active' COMMENT 'API Key 状态',
    permissions JSON COMMENT 'API Key 权限范围限制 (NULL表示继承用户权限)',
    expires_at TIMESTAMP(6) COMMENT '过期时间 (NULL表示不过期)',
    last_used_at TIMESTAMP(6) COMMENT '最后使用时间',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    created_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT '创建人',
    last_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间',
    last_updated_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT '最后更新人',
    CONSTRAINT user_api_keys_pk PRIMARY KEY (id),
    CONSTRAINT user_api_keys_key_hash_uk UNIQUE (key_hash),
    CONSTRAINT user_api_keys_user_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT user_api_keys_workspace_fk FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    INDEX idx_user_api_keys_user (user_id),
    INDEX idx_user_api_keys_workspace (workspace_id),
    INDEX idx_user_api_keys_status (status),
    INDEX idx_user_api_keys_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户API Key表';

--rollback DROP TABLE IF EXISTS user_api_keys;
```

### 1.7 Session 表 (user_sessions)

```sql
--liquibase formatted sql
--changeset system:add_user_sessions_table
--comment: 创建用户Session表

CREATE TABLE user_sessions (
    id CHAR(36) NOT NULL COMMENT 'Session ID (UUID)',
    session_token VARCHAR(255) NOT NULL COMMENT 'Session Token (唯一)',
    user_id CHAR(36) NOT NULL COMMENT '用户ID',
    ip_address VARCHAR(45) COMMENT 'IP地址 (支持IPv6)',
    user_agent TEXT COMMENT 'User Agent',
    fingerprint VARCHAR(64) COMMENT 'Session指纹 (IP+UserAgent哈希), 用于防劫持',
    expires_at TIMESTAMP(6) NOT NULL COMMENT '过期时间',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    last_accessed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最后访问时间',
    CONSTRAINT user_sessions_pk PRIMARY KEY (id),
    CONSTRAINT user_sessions_token_uk UNIQUE (session_token),
    -- 注意: 生产环境建议移除外键约束
    -- CONSTRAINT user_sessions_user_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_sessions_user (user_id),
    INDEX idx_user_sessions_expires (expires_at),
    INDEX idx_user_sessions_token (session_token),
    INDEX idx_user_sessions_fingerprint (fingerprint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户Session表';

--rollback DROP TABLE IF EXISTS user_sessions;
```

### 1.8 密码重置令牌表 (password_reset_tokens)

```sql
--liquibase formatted sql
--changeset system:add_password_reset_tokens_table
--comment: 创建密码重置令牌表

CREATE TABLE password_reset_tokens (
    id CHAR(36) NOT NULL COMMENT '令牌ID (UUID)',
    user_id CHAR(36) NOT NULL COMMENT '用户ID',
    token VARCHAR(255) NOT NULL COMMENT '重置令牌 (唯一)',
    expires_at TIMESTAMP(6) NOT NULL COMMENT '过期时间',
    used_at TIMESTAMP(6) COMMENT '使用时间 (NULL表示未使用)',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    CONSTRAINT password_reset_tokens_pk PRIMARY KEY (id),
    CONSTRAINT password_reset_tokens_token_uk UNIQUE (token),
    CONSTRAINT password_reset_tokens_user_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_password_reset_tokens_user (user_id),
    INDEX idx_password_reset_tokens_expires (expires_at),
    INDEX idx_password_reset_tokens_token (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='密码重置令牌表';

--rollback DROP TABLE IF EXISTS password_reset_tokens;
```

## 2. ClickHouse 表结构设计

### 2.1 审计日志表 (audit_logs)

```sql
--liquibase formatted sql
--changeset system:add_audit_logs_table
--comment: 创建审计日志表,记录所有关键操作

CREATE TABLE IF NOT EXISTS ${ANALYTICS_DB_DATABASE_NAME}.audit_logs ON CLUSTER '{cluster}' (
    id              FixedString(36) COMMENT '审计日志ID (UUID)',
    workspace_id    String COMMENT '工作空间ID',
    user_id         String COMMENT '用户ID',
    username        String COMMENT '用户名',
    action          String COMMENT '操作描述',
    resource_type   String COMMENT '资源类型 (project, trace, dataset, ...)',
    resource_id     String COMMENT '资源ID',
    resource_name   String COMMENT '资源名称',
    operation       Enum8(
        'create' = 1,
        'read' = 2,
        'update' = 3,
        'delete' = 4,
        'execute' = 5,
        'login' = 6,
        'logout' = 7,
        'other' = 8
    ) COMMENT '操作类型',
    status          Enum8(
        'success' = 1,
        'failure' = 2,
        'partial' = 3
    ) COMMENT '操作状态',
    ip_address      String COMMENT 'IP地址',
    user_agent      String COMMENT 'User Agent',
    request_path    String COMMENT '请求路径',
    request_method  String COMMENT '请求方法 (GET, POST, ...)',
    changes         String COMMENT '变更详情 (JSON)',
    error_message   String COMMENT '错误信息',
    duration_ms     UInt32 COMMENT '操作耗时 (毫秒)',
    timestamp       DateTime64(9, 'UTC') DEFAULT now64(9) COMMENT '操作时间戳',
    created_at      DateTime64(9, 'UTC') DEFAULT now64(9) COMMENT '创建时间',
    created_by      String DEFAULT 'system' COMMENT '创建人'
)
ENGINE = ReplicatedReplacingMergeTree('/clickhouse/tables/{shard}/${ANALYTICS_DB_DATABASE_NAME}/audit_logs', '{replica}', created_at)
ORDER BY (workspace_id, timestamp, user_id, action, resource_type, id)
PARTITION BY toYYYYMM(timestamp)
SETTINGS index_granularity = 8192
COMMENT '审计日志表';

-- 添加索引优化查询性能
ALTER TABLE ${ANALYTICS_DB_DATABASE_NAME}.audit_logs ON CLUSTER '{cluster}' 
ADD INDEX idx_user_id user_id TYPE bloom_filter GRANULARITY 4;

ALTER TABLE ${ANALYTICS_DB_DATABASE_NAME}.audit_logs ON CLUSTER '{cluster}' 
ADD INDEX idx_action action TYPE bloom_filter GRANULARITY 4;

ALTER TABLE ${ANALYTICS_DB_DATABASE_NAME}.audit_logs ON CLUSTER '{cluster}' 
ADD INDEX idx_resource_type resource_type TYPE set(100) GRANULARITY 4;

ALTER TABLE ${ANALYTICS_DB_DATABASE_NAME}.audit_logs ON CLUSTER '{cluster}' 
ADD INDEX idx_operation operation TYPE set(10) GRANULARITY 4;

ALTER TABLE ${ANALYTICS_DB_DATABASE_NAME}.audit_logs ON CLUSTER '{cluster}' 
ADD INDEX idx_status status TYPE set(5) GRANULARITY 4;

--rollback DROP TABLE IF EXISTS ${ANALYTICS_DB_DATABASE_NAME}.audit_logs ON CLUSTER '{cluster}';
```

## 3. 数据关系图

```
┌─────────────┐
│   users     │
└──────┬──────┘
       │ 1
       │ owns
       │ *
┌──────┴──────────┐
│   workspaces    │◀──────┐
└──────┬──────────┘       │
       │ *                │ *
       │ has              │ belongs to
       │ *                │ *
┌──────┴──────────┐  ┌────┴────────────┐
│workspace_members│  │  user_api_keys  │
└──────┬──────────┘  └─────────────────┘
       │ *
       │ has
       │ 1
┌──────┴──────┐       ┌──────────────────┐
│    roles    │       │  user_sessions   │
└──────┬──────┘       └──────────────────┘
       │ *                     │ *
       │ assigned              │ belongs to
       │ *                     │ 1
┌──────┴─────────┐      ┌──────┴──────┐
│project_members │      │   users     │
└────────────────┘      └─────────────┘

Relationships:
- users 1:* workspaces (owner_user_id)
- users 1:* workspace_members (user_id)
- workspaces 1:* workspace_members (workspace_id)
- roles 1:* workspace_members (role_id)
- users 1:* user_api_keys (user_id)
- workspaces 1:* user_api_keys (workspace_id)
- users 1:* user_sessions (user_id)
- projects 1:* project_members (project_id)
- users 1:* project_members (user_id)
- roles 1:* project_members (role_id)
```

## 4. 数据迁移策略

### 4.1 迁移步骤

```sql
-- Step 1: 创建新表
-- 执行上述所有建表脚本

-- Step 2: 迁移现有数据到默认工作空间
-- 已在建表脚本中完成:
--   - 创建 admin 用户
--   - 创建 default 工作空间
--   - 将 admin 用户添加为默认工作空间管理员

-- Step 3: 关联现有项目到默认工作空间
-- projects 表已经有 workspace_id 字段,值为 '0190babc-62a0-71d2-832a-0feffa4676eb'
-- 无需迁移

-- Step 4: 关联现有数据集到默认工作空间
-- datasets 表已经有 workspace_id 字段,值为 '0190babc-62a0-71d2-832a-0feffa4676eb'
-- 无需迁移

-- Step 5: 关联现有反馈定义到默认工作空间
-- feedback_definitions 表已经有 workspace_id 字段,值为 '0190babc-62a0-71d2-832a-0feffa4676eb'
-- 无需迁移
```

### 4.2 向后兼容

- 默认工作空间 ID: `0190babc-62a0-71d2-832a-0feffa4676eb` (与现有一致)
- 默认工作空间名称: `default`
- 默认用户: `admin`
- 现有数据自动关联到默认工作空间

## 5. 索引优化

### 5.1 MySQL 索引策略

1. **主键索引**: 所有表使用 UUID 作为主键
2. **唯一索引**: username, email, workspace name, session token 等
3. **外键索引**: 所有外键字段自动创建索引
4. **查询索引**: 基于常用查询模式创建复合索引

### 5.2 ClickHouse 索引策略

1. **主键排序**: `ORDER BY (workspace_id, timestamp, user_id, action, resource_type, id)`
2. **Bloom Filter**: user_id, action (精确匹配查询)
3. **Set Index**: resource_type, operation, status (低基数字段)
4. **分区**: 按月分区 `PARTITION BY toYYYYMM(timestamp)`

## 6. 性能优化建议

### 6.1 MySQL 优化

1. **连接池**: 配置合适的连接池大小
2. **批量操作**: 使用批量插入/更新减少数据库往返
3. **事务管理**: 合理使用事务,避免长事务
4. **慢查询日志**: 监控和优化慢查询

### 6.2 ClickHouse 优化

1. **批量写入**: 审计日志批量写入,默认 100 条/批
2. **异步写入**: 使用异步方式写入审计日志
3. **分区管理**: 定期清理过期分区
4. **物化视图**: 对于复杂统计查询,考虑使用物化视图

## 7. 并发控制和性能优化

### 7.1 乐观锁实现

所有核心业务表（users, workspaces, roles等）都添加了 `version` 字段用于乐观锁控制：

```java
// DAO 层更新示例
@SqlUpdate("UPDATE users SET email = :email, full_name = :fullName, " +
           "version = version + 1, last_updated_by = :updatedBy " +
           "WHERE id = :id AND version = :version")
int updateUserWithVersion(@Bind("id") String id,
                          @Bind("email") String email,
                          @Bind("fullName") String fullName,
                          @Bind("version") int version,
                          @Bind("updatedBy") String updatedBy);

// Service 层重试逻辑
@Retryable(value = OptimisticLockException.class, maxAttempts = 3)
public User updateUser(String userId, UserUpdateRequest request) {
    User user = getUser(userId).orElseThrow();
    
    int updated = userDAO.updateUserWithVersion(
        userId,
        request.getEmail(),
        request.getFullName(),
        user.getVersion(),  // 当前版本号
        requestContext.get().getUserName()
    );
    
    if (updated == 0) {
        throw new OptimisticLockException("User was modified by another transaction");
    }
    
    return getUser(userId).orElseThrow();
}
```

### 7.2 Session 防劫持设计

Session 表添加了 `fingerprint` 字段，通过 IP + User Agent 生成指纹：

```java
// 创建 Session 时生成指纹
public Session createSession(String userId, String ipAddress, String userAgent) {
    String fingerprint = generateFingerprint(ipAddress, userAgent);
    
    Session session = Session.builder()
        .id(UUID.randomUUID().toString())
        .sessionToken(generateSecureToken())
        .userId(userId)
        .ipAddress(ipAddress)
        .userAgent(userAgent)
        .fingerprint(fingerprint)
        .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
        .build();
    
    sessionDAO.save(session);
    return session;
}

// 验证 Session 时检查指纹
public Optional<Session> validateSession(String sessionToken, 
                                         String currentIp, 
                                         String currentUserAgent) {
    Optional<Session> session = sessionDAO.findByToken(sessionToken);
    
    if (session.isPresent()) {
        String currentFingerprint = generateFingerprint(currentIp, currentUserAgent);
        
        // 指纹不匹配，可能被劫持
        if (!session.get().getFingerprint().equals(currentFingerprint)) {
            log.warn("Session fingerprint mismatch: expected={}, actual={}, userId={}", 
                session.get().getFingerprint(), currentFingerprint, session.get().getUserId());
            
            // 删除可疑 Session
            sessionDAO.deleteByToken(sessionToken);
            return Optional.empty();
        }
    }
    
    return session;
}

private String generateFingerprint(String ipAddress, String userAgent) {
    String data = ipAddress + "|" + userAgent;
    return DigestUtils.sha256Hex(data);
}
```

### 7.3 数据库性能优化建议

#### 1. 外键约束

生产环境建议移除外键约束，在应用层保证引用完整性：

```sql
-- 开发/测试环境: 保留外键约束，便于数据一致性检查
CONSTRAINT workspaces_owner_fk FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE RESTRICT

-- 生产环境: 移除外键约束，提升性能
-- 在应用层保证引用完整性
```

#### 2. 索引优化

```sql
-- 覆盖索引示例 (workspace_members 表)
CREATE INDEX idx_workspace_members_cover 
ON workspace_members(workspace_id, user_id, role_id, status);

-- 查询可以完全从索引获取数据，无需回表
SELECT workspace_id, user_id, role_id, status
FROM workspace_members
WHERE workspace_id = 'xxx' AND status = 'active';
```

#### 3. ENUM 改为 VARCHAR

使用 VARCHAR + CHECK 约束替代 ENUM，便于后续扩展：

```sql
-- ❌ ENUM: 添加新值需要 ALTER TABLE
status ENUM('active', 'suspended', 'deleted')

-- ✅ VARCHAR + CHECK: 可以在应用层控制
status VARCHAR(20) NOT NULL DEFAULT 'active',
CONSTRAINT users_status_check CHECK (status IN ('active', 'suspended', 'deleted'))
```

### 7.4 批量操作优化

```java
// 批量加载，避免 N+1 查询
public List<WorkspaceMemberDTO> getWorkspaceMembers(String workspaceId) {
    // 1. 加载所有成员
    List<WorkspaceMember> members = memberDAO.findByWorkspace(workspaceId);
    
    // 2. 批量加载用户信息
    Set<String> userIds = members.stream()
        .map(WorkspaceMember::getUserId)
        .collect(Collectors.toSet());
    
    Map<String, User> userMap = userDAO.findByIds(userIds)
        .stream()
        .collect(Collectors.toMap(User::getId, Function.identity()));
    
    // 3. 批量加载角色信息
    Set<String> roleIds = members.stream()
        .map(WorkspaceMember::getRoleId)
        .collect(Collectors.toSet());
    
    Map<String, Role> roleMap = roleDAO.findByIds(roleIds)
        .stream()
        .collect(Collectors.toMap(Role::getId, Function.identity()));
    
    // 4. 组装 DTO
    return members.stream()
        .map(m -> WorkspaceMemberDTO.builder()
            .member(m)
            .user(userMap.get(m.getUserId()))
            .role(roleMap.get(m.getRoleId()))
            .build())
        .collect(Collectors.toList());
}
```

## 8. 数据备份策略

### 8.1 MySQL 备份

- 定时全量备份 (每天)
- 增量备份 (每小时)
- 保留 30 天备份

### 8.2 ClickHouse 备份

- 审计日志分区备份
- 保留 90 天数据 (可配置)
- 过期数据自动归档/删除

下一章: [RBAC 权限设计](./04-rbac-design.md)

