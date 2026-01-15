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
     * System admin marker
     */
    SYSTEM_ADMIN,

    /**
     * Create, update, delete users
     */
    SYSTEM_USER_MANAGE,

    /**
     * Create, update, delete workspaces
     */
    SYSTEM_WORKSPACE_MANAGE,

    /**
     * Manage system settings
     */
    SYSTEM_SETTINGS,

    /**
     * View audit logs
     */
    SYSTEM_AUDIT_VIEW,

    // ==================== WORKSPACE PERMISSIONS ====================

    /**
     * Workspace admin marker
     */
    WORKSPACE_ADMIN,

    /**
     * View workspace details
     */
    WORKSPACE_VIEW,

    /**
     * Update workspace settings
     */
    WORKSPACE_SETTINGS,

    /**
     * Add/remove members
     */
    WORKSPACE_MEMBER_MANAGE,

    /**
     * View workspace members
     */
    WORKSPACE_MEMBER_VIEW,

    // ==================== PROJECT PERMISSIONS ====================

    /**
     * Create projects
     */
    PROJECT_CREATE,

    /**
     * View project details
     */
    PROJECT_VIEW,

    /**
     * Update project settings
     */
    PROJECT_UPDATE,

    /**
     * Delete project
     */
    PROJECT_DELETE,

    /**
     * Create traces
     */
    TRACE_CREATE,

    /**
     * View traces
     */
    TRACE_VIEW,

    /**
     * Update traces
     */
    TRACE_UPDATE,

    /**
     * Delete traces
     */
    TRACE_DELETE,

    /**
     * Create datasets
     */
    DATASET_CREATE,

    /**
     * View datasets
     */
    DATASET_VIEW,

    /**
     * Update datasets
     */
    DATASET_UPDATE,

    /**
     * Delete datasets
     */
    DATASET_DELETE,

    /**
     * Create prompts
     */
    PROMPT_CREATE,

    /**
     * View prompts
     */
    PROMPT_VIEW,

    /**
     * Update prompts
     */
    PROMPT_UPDATE,

    /**
     * Delete prompts
     */
    PROMPT_DELETE,

    /**
     * Create experiments
     */
    EXPERIMENT_CREATE,

    /**
     * View experiments
     */
    EXPERIMENT_VIEW,

    /**
     * Update experiments
     */
    EXPERIMENT_UPDATE,

    /**
     * Delete experiments
     */
    EXPERIMENT_DELETE,

    /**
     * Generate API keys
     */
    API_KEY_CREATE,

    /**
     * View API keys
     */
    API_KEY_VIEW,

    /**
     * Revoke API keys
     */
    API_KEY_REVOKE,

    /**
     * Create feedback definitions
     */
    FEEDBACK_DEFINITION_CREATE,

    /**
     * View feedback definitions
     */
    FEEDBACK_DEFINITION_VIEW,

    /**
     * Update feedback definitions
     */
    FEEDBACK_DEFINITION_UPDATE,

    /**
     * Delete feedback definitions
     */
    FEEDBACK_DEFINITION_DELETE;

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
