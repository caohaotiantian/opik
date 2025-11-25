package com.comet.opik.infrastructure.authorization;

import com.comet.opik.infrastructure.auth.RequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.ForbiddenException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * AOP interceptor for @RequiresPermission annotation
 *
 * Automatically checks permissions before method execution.
 *
 * Permission check flow:
 * 1. Check if method has @RequiresPermission annotation
 * 2. Extract required permissions from annotation
 * 3. Check if user is System Admin (automatic pass)
 * 4. Check workspace or project permissions based on annotation
 * 5. Throw ForbiddenException if permission check fails
 * 6. Proceed with method execution if permission check passes
 *
 * Usage:
 * Install this interceptor in Guice module using bindInterceptor()
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PermissionInterceptor implements MethodInterceptor {

    private final @NonNull Provider<RequestContext> requestContextProvider;
    private final @NonNull PermissionService permissionService;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();

        // Check if method has @RequiresPermission annotation
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);
        if (annotation == null) {
            // No permission check required
            return invocation.proceed();
        }

        RequestContext context = requestContextProvider.get();

        // Extract required permissions
        String[] requiredPermissions = annotation.value();
        boolean requireAll = annotation.requireAll();
        boolean checkProjectLevel = annotation.checkProjectLevel();
        String customMessage = annotation.message();

        log.debug("Permission check triggered for method: '{}', user: '{}', permissions: {}, requireAll: {}",
                method.getName(), context.getUserName(), Arrays.toString(requiredPermissions), requireAll);

        // 1. System Admin check - automatic pass
        if (context.isSystemAdmin()) {
            log.debug("User '{}' is System Admin, permission check passed", context.getUserName());
            return invocation.proceed();
        }

        // 2. Validate context
        String userId = context.getUserId();
        String workspaceId = context.getWorkspaceId();

        if (StringUtils.isBlank(userId) || StringUtils.isBlank(workspaceId)) {
            log.error("Invalid request context: userId='{}', workspaceId='{}'", userId, workspaceId);
            throw new ForbiddenException("Authentication context is missing");
        }

        // 3. Perform permission check
        boolean hasPermission;

        if (checkProjectLevel) {
            // Check project-level permissions
            String projectId = context.getProjectId();

            if (StringUtils.isBlank(projectId)) {
                log.error("Project ID is missing in context for project-level permission check");
                throw new ForbiddenException("Project context is missing");
            }

            hasPermission = permissionService.hasProjectPermission(
                    userId,
                    workspaceId,
                    projectId,
                    requiredPermissions,
                    requireAll);
        } else {
            // Check workspace-level permissions
            hasPermission = permissionService.hasWorkspacePermission(
                    userId,
                    workspaceId,
                    requiredPermissions,
                    requireAll);
        }

        // 4. Handle permission check result
        if (!hasPermission) {
            // Permission denied
            String errorMessage;
            if (StringUtils.isNotBlank(customMessage)) {
                errorMessage = customMessage;
            } else {
                errorMessage = buildDefaultErrorMessage(method, requiredPermissions, requireAll);
            }

            log.warn("Permission denied for user: '{}', method: '{}', required: {}, requireAll: {}",
                    context.getUserName(), method.getName(), Arrays.toString(requiredPermissions), requireAll);

            throw new ForbiddenException(errorMessage);
        }

        // 5. Permission check passed, proceed with method execution
        log.debug("Permission check passed for user: '{}', method: '{}'",
                context.getUserName(), method.getName());

        return invocation.proceed();
    }

    /**
     * Build default error message for permission denial
     */
    private String buildDefaultErrorMessage(Method method, String[] requiredPermissions, boolean requireAll) {
        if (requiredPermissions.length == 1) {
            return String.format("Insufficient permissions to perform this operation. Required permission: %s",
                    requiredPermissions[0]);
        } else if (requireAll) {
            return String.format(
                    "Insufficient permissions to perform this operation. Required permissions (all): %s",
                    Arrays.toString(requiredPermissions));
        } else {
            return String.format(
                    "Insufficient permissions to perform this operation. Required permissions (any): %s",
                    Arrays.toString(requiredPermissions));
        }
    }
}
