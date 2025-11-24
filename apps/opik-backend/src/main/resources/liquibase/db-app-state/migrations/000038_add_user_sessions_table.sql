--liquibase formatted sql
--changeset multi-tenant:000038_add_user_sessions_table
--comment: Create user_sessions table to manage user login sessions

CREATE TABLE user_sessions (
    id CHAR(36) NOT NULL COMMENT 'Session ID (UUID)',
    user_id CHAR(36) NOT NULL COMMENT 'User ID',
    session_token VARCHAR(255) NOT NULL COMMENT 'Session token (stored in cookie)',
    fingerprint VARCHAR(255) NOT NULL COMMENT 'Browser fingerprint for security',
    ip_address VARCHAR(50) COMMENT 'IP address',
    user_agent VARCHAR(500) COMMENT 'User agent string',
    last_activity_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Last activity timestamp',
    expires_at TIMESTAMP(6) NOT NULL COMMENT 'Expiration timestamp',
    version INT NOT NULL DEFAULT 0 COMMENT 'Version number for optimistic locking',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation timestamp',
    created_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT 'Creator',
    last_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update timestamp',
    last_updated_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT 'Last updater',
    CONSTRAINT user_sessions_pk PRIMARY KEY (id),
    CONSTRAINT user_sessions_token_uk UNIQUE (session_token),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User sessions table for session-based authentication';

-- Index for session token lookups (most frequent query for authentication)
CREATE INDEX idx_user_sessions_token ON user_sessions(session_token);

-- Index for user lookups (list user's active sessions)
CREATE INDEX idx_user_sessions_user ON user_sessions(user_id);

-- Index for expiration cleanup (automated cleanup job)
CREATE INDEX idx_user_sessions_expires ON user_sessions(expires_at);

-- Composite index for token + expires_at (fast session validation)
CREATE INDEX idx_user_sessions_auth ON user_sessions(session_token, expires_at);

--rollback DROP INDEX IF EXISTS idx_user_sessions_auth ON user_sessions;
--rollback DROP INDEX IF EXISTS idx_user_sessions_expires ON user_sessions;
--rollback DROP INDEX IF EXISTS idx_user_sessions_user ON user_sessions;
--rollback DROP INDEX IF EXISTS idx_user_sessions_token ON user_sessions;
--rollback DROP TABLE IF EXISTS user_sessions;


