--liquibase formatted sql
--changeset multi-tenant:000032_add_users_table
--comment: Create users table to support multi-user system with authentication

CREATE TABLE users (
    id CHAR(36) NOT NULL COMMENT 'User ID (UUID)',
    username VARCHAR(100) NOT NULL COMMENT 'Username for login (unique)',
    email VARCHAR(255) NOT NULL COMMENT 'Email address (unique)',
    password_hash VARCHAR(255) NOT NULL COMMENT 'Password hash (BCrypt)',
    full_name VARCHAR(255) COMMENT 'User full name',
    avatar_url VARCHAR(500) COMMENT 'Avatar URL',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'User status: active, suspended, deleted',
    is_system_admin BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Whether user is system administrator',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Whether email is verified',
    last_login_at TIMESTAMP(6) COMMENT 'Last login timestamp',
    locale VARCHAR(10) DEFAULT 'en-US' COMMENT 'User language preference',
    version INT NOT NULL DEFAULT 0 COMMENT 'Version number for optimistic locking',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation timestamp',
    created_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT 'Creator',
    last_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update timestamp',
    last_updated_by VARCHAR(100) NOT NULL DEFAULT 'system' COMMENT 'Last updater',
    CONSTRAINT users_pk PRIMARY KEY (id),
    CONSTRAINT users_username_uk UNIQUE (username),
    CONSTRAINT users_email_uk UNIQUE (email),
    CONSTRAINT users_status_check CHECK (status IN ('active', 'suspended', 'deleted'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Users table for multi-user authentication system';

-- Index for email lookups (frequent login queries)
CREATE INDEX idx_users_email ON users(email);

-- Index for status filtering (admin user management)
CREATE INDEX idx_users_status ON users(status);

-- Index for system admin queries
CREATE INDEX idx_users_system_admin ON users(is_system_admin);

-- Insert default system administrator
-- Password: Admin@123 (BCrypt hash with cost=12)
INSERT INTO users (
    id, 
    username, 
    email, 
    password_hash, 
    full_name, 
    is_system_admin, 
    status,
    email_verified
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin',
    'admin@opik.local',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYb8Fo0e5A6',
    'System Administrator',
    TRUE,
    'active',
    TRUE
);

--rollback DROP INDEX IF EXISTS idx_users_system_admin ON users;
--rollback DROP INDEX IF EXISTS idx_users_status ON users;
--rollback DROP INDEX IF EXISTS idx_users_email ON users;
--rollback DELETE FROM users WHERE id = '00000000-0000-0000-0000-000000000001';
--rollback DROP TABLE IF EXISTS users;


