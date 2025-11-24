--liquibase formatted sql
--changeset multi-tenant:000039_add_password_reset_tokens_table
--comment: Create password_reset_tokens table to manage password reset flow

CREATE TABLE password_reset_tokens (
    id CHAR(36) NOT NULL COMMENT 'Token ID (UUID)',
    user_id CHAR(36) NOT NULL COMMENT 'User ID',
    token VARCHAR(255) NOT NULL COMMENT 'Reset token',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'Token status: pending, used, expired',
    ip_address VARCHAR(50) COMMENT 'IP address that requested reset',
    used_at TIMESTAMP(6) COMMENT 'Timestamp when token was used',
    expires_at TIMESTAMP(6) NOT NULL COMMENT 'Expiration timestamp',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation timestamp',
    created_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT 'Creator',
    last_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update timestamp',
    last_updated_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT 'Last updater',
    CONSTRAINT password_reset_tokens_pk PRIMARY KEY (id),
    CONSTRAINT password_reset_tokens_token_uk UNIQUE (token),
    CONSTRAINT password_reset_tokens_status_check CHECK (status IN ('pending', 'used', 'expired')),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Password reset tokens table for password recovery flow';

-- Index for token lookups (most frequent query)
CREATE INDEX idx_password_reset_tokens_token ON password_reset_tokens(token);

-- Index for user lookups (find user's reset tokens)
CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens(user_id);

-- Index for expiration cleanup (automated cleanup job)
CREATE INDEX idx_password_reset_tokens_expires ON password_reset_tokens(expires_at);

-- Composite index for token + status (fast validation)
CREATE INDEX idx_password_reset_tokens_auth ON password_reset_tokens(token, status);

--rollback DROP INDEX IF EXISTS idx_password_reset_tokens_auth ON password_reset_tokens;
--rollback DROP INDEX IF EXISTS idx_password_reset_tokens_expires ON password_reset_tokens;
--rollback DROP INDEX IF EXISTS idx_password_reset_tokens_user ON password_reset_tokens;
--rollback DROP INDEX IF EXISTS idx_password_reset_tokens_token ON password_reset_tokens;
--rollback DROP TABLE IF EXISTS password_reset_tokens;


