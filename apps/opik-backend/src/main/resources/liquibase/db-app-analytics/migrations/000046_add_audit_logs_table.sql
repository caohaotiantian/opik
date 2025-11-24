--liquibase formatted sql
--changeset multi-tenant:000046_add_audit_logs_table
--comment: Create audit_logs table in ClickHouse to store audit trail for multi-user system

CREATE TABLE IF NOT EXISTS ${ANALYTICS_DB_DATABASE_NAME}.audit_logs
(
    `id` FixedString(36) COMMENT 'Audit log ID (UUID)',
    `timestamp` DateTime64(9, 'UTC') DEFAULT now64(9) COMMENT 'Timestamp of the action',
    `workspace_id` String COMMENT 'Workspace ID',
    `user_id` FixedString(36) COMMENT 'User ID who performed the action',
    `username` String COMMENT 'Username for quick reference',
    `action` String COMMENT 'Action performed (e.g., CREATE, UPDATE, DELETE, LOGIN)',
    `resource_type` String COMMENT 'Type of resource (e.g., PROJECT, TRACE, DATASET, USER)',
    `resource_id` String DEFAULT '' COMMENT 'Resource ID (if applicable)',
    `resource_name` String DEFAULT '' COMMENT 'Resource name for quick reference',
    `ip_address` String DEFAULT '' COMMENT 'IP address of the request',
    `user_agent` String DEFAULT '' COMMENT 'User agent string',
    `request_id` String DEFAULT '' COMMENT 'Request ID for correlation',
    `result` Enum8('success' = 1, 'failure' = 2, 'error' = 3) COMMENT 'Result of the action',
    `error_message` String DEFAULT '' COMMENT 'Error message if result is failure or error',
    `metadata` String DEFAULT '' COMMENT 'Additional metadata in JSON format',
    `created_at` DateTime64(9, 'UTC') DEFAULT now64(9) COMMENT 'Record creation timestamp',
    `created_by` String DEFAULT 'system' COMMENT 'Creator (always system for audit logs)'
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/${ANALYTICS_DB_DATABASE_NAME}/audit_logs', '{replica}')
ORDER BY (workspace_id, timestamp, user_id, action, resource_type, id)
TTL toDateTime(timestamp + toIntervalMonth(12))
SETTINGS index_granularity = 8192
COMMENT 'Audit logs table for tracking all user actions in the system';

-- Add index for timestamp-based queries (most common audit log query pattern)
ALTER TABLE ${ANALYTICS_DB_DATABASE_NAME}.audit_logs 
ADD INDEX idx_audit_timestamp timestamp TYPE minmax GRANULARITY 4;

-- Add index for user_id lookups (track user activities)
ALTER TABLE ${ANALYTICS_DB_DATABASE_NAME}.audit_logs 
ADD INDEX idx_audit_user_id user_id TYPE set(100) GRANULARITY 4;

-- Add index for action filtering (find specific action types)
ALTER TABLE ${ANALYTICS_DB_DATABASE_NAME}.audit_logs 
ADD INDEX idx_audit_action action TYPE set(50) GRANULARITY 4;

-- Add index for resource_type filtering (find actions on specific resource types)
ALTER TABLE ${ANALYTICS_DB_DATABASE_NAME}.audit_logs 
ADD INDEX idx_audit_resource_type resource_type TYPE set(50) GRANULARITY 4;

-- Add index for result filtering (find failures and errors)
ALTER TABLE ${ANALYTICS_DB_DATABASE_NAME}.audit_logs 
ADD INDEX idx_audit_result result TYPE set(3) GRANULARITY 4;

--rollback ALTER TABLE ${ANALYTICS_DB_DATABASE_NAME}.audit_logs DROP INDEX IF EXISTS idx_audit_result;
--rollback ALTER TABLE ${ANALYTICS_DB_DATABASE_NAME}.audit_logs DROP INDEX IF EXISTS idx_audit_resource_type;
--rollback ALTER TABLE ${ANALYTICS_DB_DATABASE_NAME}.audit_logs DROP INDEX IF EXISTS idx_audit_action;
--rollback ALTER TABLE ${ANALYTICS_DB_DATABASE_NAME}.audit_logs DROP INDEX IF EXISTS idx_audit_user_id;
--rollback ALTER TABLE ${ANALYTICS_DB_DATABASE_NAME}.audit_logs DROP INDEX IF EXISTS idx_audit_timestamp;
--rollback DROP TABLE IF EXISTS ${ANALYTICS_DB_DATABASE_NAME}.audit_logs;


