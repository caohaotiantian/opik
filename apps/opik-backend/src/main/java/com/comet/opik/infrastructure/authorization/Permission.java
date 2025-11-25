package com.comet.opik.infrastructure.authorization;

/**
 * Permission enumeration for RBAC system
 *
 * Permissions are organized by scope:
 * - SYSTEM: System-wide administrative permissions
 * - WORKSPACE: Workspace-level permissions
 * - PROJECT: Project-level permissions
 */
public enum Permission {

    // ==================== SYSTEM PERMISSIONS ====================

    /**
     * Create, update, delete users
     */
    SYSTEM_USER_MANAGE,

    /**
     * View all users
     */
    SYSTEM_USER_READ,

    /**
     * Create, update, delete workspaces
     */
    SYSTEM_WORKSPACE_MANAGE,

    /**
     * View all workspaces
     */
    SYSTEM_WORKSPACE_READ,

    /**
     * Manage system configuration
     */
    SYSTEM_CONFIG_MANAGE,

    /**
     * View system configuration
     */
    SYSTEM_CONFIG_READ,

    /**
     * View audit logs
     */
    SYSTEM_AUDIT_READ,

    // ==================== WORKSPACE PERMISSIONS ====================

    /**
     * Delete workspace
     */
    WORKSPACE_DELETE,

    /**
     * Update workspace settings
     */
    WORKSPACE_UPDATE,

    /**
     * View workspace details
     */
    WORKSPACE_READ,

    /**
     * Add/remove members
     */
    WORKSPACE_MEMBER_MANAGE,

    /**
     * Update member roles
     */
    WORKSPACE_MEMBER_UPDATE,

    /**
     * View workspace members
     */
    WORKSPACE_MEMBER_READ,

    /**
     * Create projects
     */
    WORKSPACE_PROJECT_CREATE,

    /**
     * View all projects in workspace
     */
    WORKSPACE_PROJECT_READ,

    /**
     * Generate API keys
     */
    WORKSPACE_API_KEY_CREATE,

    /**
     * Revoke API keys
     */
    WORKSPACE_API_KEY_REVOKE,

    /**
     * View API keys
     */
    WORKSPACE_API_KEY_READ,

    // ==================== PROJECT PERMISSIONS ====================

    /**
     * Delete project
     */
    PROJECT_DELETE,

    /**
     * Update project settings
     */
    PROJECT_UPDATE,

    /**
     * View project details
     */
    PROJECT_READ,

    /**
     * Add/remove project members
     */
    PROJECT_MEMBER_MANAGE,

    /**
     * Update project member roles
     */
    PROJECT_MEMBER_UPDATE,

    /**
     * View project members
     */
    PROJECT_MEMBER_READ,

    /**
     * Create traces
     */
    PROJECT_TRACE_CREATE,

    /**
     * Update traces
     */
    PROJECT_TRACE_UPDATE,

    /**
     * Delete traces
     */
    PROJECT_TRACE_DELETE,

    /**
     * View traces
     */
    PROJECT_TRACE_READ,

    /**
     * Create spans
     */
    PROJECT_SPAN_CREATE,

    /**
     * Update spans
     */
    PROJECT_SPAN_UPDATE,

    /**
     * Delete spans
     */
    PROJECT_SPAN_DELETE,

    /**
     * View spans
     */
    PROJECT_SPAN_READ,

    /**
     * Create datasets
     */
    PROJECT_DATASET_CREATE,

    /**
     * Update datasets
     */
    PROJECT_DATASET_UPDATE,

    /**
     * Delete datasets
     */
    PROJECT_DATASET_DELETE,

    /**
     * View datasets
     */
    PROJECT_DATASET_READ,

    /**
     * Create experiments
     */
    PROJECT_EXPERIMENT_CREATE,

    /**
     * Update experiments
     */
    PROJECT_EXPERIMENT_UPDATE,

    /**
     * Delete experiments
     */
    PROJECT_EXPERIMENT_DELETE,

    /**
     * View experiments
     */
    PROJECT_EXPERIMENT_READ,

    /**
     * Add feedback scores
     */
    PROJECT_FEEDBACK_CREATE,

    /**
     * Update feedback scores
     */
    PROJECT_FEEDBACK_UPDATE,

    /**
     * Delete feedback scores
     */
    PROJECT_FEEDBACK_DELETE,

    /**
     * View feedback scores
     */
    PROJECT_FEEDBACK_READ;

    /**
     * Check if this is a system permission
     */
    public boolean isSystemPermission() {
        return this.name().startsWith("SYSTEM_");
    }

    /**
     * Check if this is a workspace permission
     */
    public boolean isWorkspacePermission() {
        return this.name().startsWith("WORKSPACE_");
    }

    /**
     * Check if this is a project permission
     */
    public boolean isProjectPermission() {
        return this.name().startsWith("PROJECT_");
    }
}
