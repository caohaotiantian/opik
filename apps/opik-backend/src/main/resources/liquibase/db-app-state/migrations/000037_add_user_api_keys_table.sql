--liquibase formatted sql
--changeset multi-tenant:000037_add_user_api_keys_table
--comment: Create user_api_keys table to manage API key authentication

CREATE TABLE user_api_keys (
    id CHAR(36) NOT NULL COMMENT 'API Key ID (UUID)',
    user_id CHAR(36) NOT NULL COMMENT 'User ID',
    workspace_id CHAR(36) NOT NULL COMMENT 'Workspace ID',
    name VARCHAR(255) NOT NULL COMMENT 'API Key name',
    key_hash VARCHAR(255) NOT NULL COMMENT 'API Key hash (SHA-256)',
    key_prefix VARCHAR(10) NOT NULL COMMENT 'Key prefix for display (e.g., opik_1234)',
    permissions JSON COMMENT 'Permission list (JSON array), NULL means inherit user permissions',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'API Key status: active, revoked, expired',
    last_used_at TIMESTAMP(6) COMMENT 'Last used timestamp',
    last_used_ip VARCHAR(50) COMMENT 'Last used IP address',
    expires_at TIMESTAMP(6) COMMENT 'Expiration timestamp, NULL means never expires',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation timestamp',
    created_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT 'Creator',
    last_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update timestamp',
    last_updated_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT 'Last updater',
    CONSTRAINT user_api_keys_pk PRIMARY KEY (id),
    CONSTRAINT user_api_keys_hash_uk UNIQUE (key_hash),
    CONSTRAINT user_api_keys_status_check CHECK (status IN ('active', 'revoked', 'expired')),
    CONSTRAINT fk_user_api_keys_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_api_keys_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API Keys table for API authentication';

-- Index for key hash lookups (most frequent query for authentication)
CREATE INDEX idx_user_api_keys_hash ON user_api_keys(key_hash);

-- Index for user lookups (list user's API keys)
CREATE INDEX idx_user_api_keys_user ON user_api_keys(user_id);

-- Index for workspace lookups (list workspace API keys)
CREATE INDEX idx_user_api_keys_workspace ON user_api_keys(workspace_id);

-- Index for status queries (active key checks)
CREATE INDEX idx_user_api_keys_status ON user_api_keys(status);

-- Composite index for hash + status (fast authentication validation)
CREATE INDEX idx_user_api_keys_auth ON user_api_keys(key_hash, status);

--rollback DROP INDEX IF EXISTS idx_user_api_keys_auth ON user_api_keys;
--rollback DROP INDEX IF EXISTS idx_user_api_keys_status ON user_api_keys;
--rollback DROP INDEX IF EXISTS idx_user_api_keys_workspace ON user_api_keys;
--rollback DROP INDEX IF EXISTS idx_user_api_keys_user ON user_api_keys;
--rollback DROP INDEX IF EXISTS idx_user_api_keys_hash ON user_api_keys;
--rollback DROP TABLE IF EXISTS user_api_keys;


