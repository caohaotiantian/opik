package com.comet.opik.infrastructure.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Permission check annotation for RBAC system
 *
 * Used to mark methods that require permission checks.
 * The PermissionInterceptor will automatically check permissions before method execution.
 *
 * Examples:
 *
 * // Single permission (AND logic by default)
 * @RequiresPermission("PROJECT_CREATE")
 * public Project createProject(...) { ... }
 *
 * // Multiple permissions with AND logic (requires all)
 * @RequiresPermission(value = {"PROJECT_UPDATE", "PROJECT_DELETE"}, requireAll = true)
 * public void updateProject(...) { ... }
 *
 * // Multiple permissions with OR logic (requires any)
 * @RequiresPermission(value = {"PROJECT_VIEW", "WORKSPACE_VIEW"}, requireAll = false)
 * public Dashboard getDashboard(...) { ... }
 *
 * // Project-level permission check
 * @RequiresPermission(value = "TRACE_CREATE", checkProjectLevel = true)
 * public Trace createTrace(...) { ... }
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresPermission {

    /**
     * Required permissions (Permission enum names as strings)
     *
     * Use Permission enum names like "PROJECT_CREATE", "WORKSPACE_VIEW", etc.
     *
     * @return array of permission names
     */
    String[] value();

    /**
     * Whether all permissions are required (AND logic)
     *
     * true: User must have ALL specified permissions (default)
     * false: User must have ANY of the specified permissions (OR logic)
     *
     * @return true if all permissions required, false if any permission is sufficient
     */
    boolean requireAll() default true;

    /**
     * Whether to check project-level permissions
     *
     * true: Check permissions at project level (requires projectId in context)
     * false: Check permissions at workspace level only (default)
     *
     * Note: Workspace admins automatically have all project permissions
     *
     * @return true if project-level check required, false otherwise
     */
    boolean checkProjectLevel() default false;

    /**
     * Custom error message when permission check fails
     *
     * If empty, a default error message will be used.
     *
     * @return custom error message or empty string for default
     */
    String message() default "";
}
