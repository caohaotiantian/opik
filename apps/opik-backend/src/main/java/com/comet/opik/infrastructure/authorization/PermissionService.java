package com.comet.opik.infrastructure.authorization;

import com.comet.opik.api.MemberStatus;
import com.comet.opik.domain.ProjectMemberService;
import com.comet.opik.domain.RoleService;
import com.comet.opik.domain.WorkspaceMemberService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Permission service for RBAC system
 *
 * Handles permission checking logic with three-level hierarchy:
 * 1. System Admin - automatically has all permissions
 * 2. Workspace permissions - applies to all projects in workspace
 * 3. Project permissions - specific to individual projects
 *
 * Uses Redis caching to improve performance.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PermissionService {

    private final @NonNull WorkspaceMemberService workspaceMemberService;
    private final @NonNull ProjectMemberService projectMemberService;
    private final @NonNull RoleService roleService;
    private final @NonNull PermissionCacheService cacheService;

    /**
     * Check if user has workspace-level permissions
     *
     * @param userId the user ID
     * @param workspaceId the workspace ID
     * @param requiredPermissions array of required permission names
     * @param requireAll true for AND logic, false for OR logic
     * @return true if user has required permissions
     */
    public boolean hasWorkspacePermission(
            String userId,
            String workspaceId,
            String[] requiredPermissions,
            boolean requireAll) {

        log.debug("Checking workspace permissions for user: '{}', workspace: '{}', permissions: {}, requireAll: {}",
                userId, workspaceId, Arrays.toString(requiredPermissions), requireAll);

        // Get user permissions (from cache or database)
        Set<String> userPermissions = cacheService.getWorkspacePermissions(userId, workspaceId)
                .orElseGet(() -> {
                    log.debug("Cache miss, loading permissions from database");
                    Set<String> permissions = getUserWorkspacePermissions(userId, workspaceId);
                    cacheService.cacheWorkspacePermissions(userId, workspaceId, permissions);
                    return permissions;
                });

        // Check permissions
        boolean hasPermission = checkPermissions(userPermissions, requiredPermissions, requireAll);

        log.debug("Workspace permission check result: user='{}', workspace='{}', hasPermission='{}'",
                userId, workspaceId, hasPermission);

        return hasPermission;
    }

    /**
     * Check if user has project-level permissions
     *
     * Checks both workspace and project permissions.
     * Workspace Admin automatically has all project permissions.
     *
     * @param userId the user ID
     * @param workspaceId the workspace ID
     * @param projectId the project ID
     * @param requiredPermissions array of required permission names
     * @param requireAll true for AND logic, false for OR logic
     * @return true if user has required permissions
     */
    public boolean hasProjectPermission(
            String userId,
            String workspaceId,
            String projectId,
            String[] requiredPermissions,
            boolean requireAll) {

        log.debug(
                "Checking project permissions for user: '{}', workspace: '{}', project: '{}', permissions: {}, requireAll: {}",
                userId, workspaceId, projectId, Arrays.toString(requiredPermissions), requireAll);

        // 1. Get workspace permissions (Workspace Admin has all project permissions)
        Set<String> workspacePermissions = getUserWorkspacePermissions(userId, workspaceId);

        // Check if user is Workspace Admin
        if (workspacePermissions.contains("WORKSPACE_ADMIN")) {
            log.debug("User '{}' is Workspace Admin, granting all permissions", userId);
            return true;
        }

        // 2. Get project permissions
        Set<String> projectPermissions = getUserProjectPermissions(userId, projectId);

        // 3. Merge permissions
        Set<String> allPermissions = new HashSet<>();
        allPermissions.addAll(workspacePermissions);
        allPermissions.addAll(projectPermissions);

        // 4. Check permissions
        boolean hasPermission = checkPermissions(allPermissions, requiredPermissions, requireAll);

        log.debug("Project permission check result: user='{}', project='{}', hasPermission='{}'",
                userId, projectId, hasPermission);

        return hasPermission;
    }

    /**
     * Get user's workspace permissions
     *
     * Loads permissions from database by querying workspace membership and role.
     *
     * @param userId the user ID
     * @param workspaceId the workspace ID
     * @return set of permission names
     */
    public Set<String> getUserWorkspacePermissions(String userId, String workspaceId) {
        log.debug("Loading workspace permissions for user: '{}', workspace: '{}'", userId, workspaceId);

        try {
            // Get workspace member info
            var memberOpt = workspaceMemberService.getMember(workspaceId, userId);

            if (memberOpt.isEmpty()) {
                log.debug("User '{}' is not a member of workspace '{}'", userId, workspaceId);
                return new HashSet<>();
            }

            var member = memberOpt.get();

            // Check member status
            if (member.status() != MemberStatus.ACTIVE) {
                log.debug("User '{}' membership in workspace '{}' is not active: '{}'",
                        userId, workspaceId, member.status());
                return new HashSet<>();
            }

            // Get role permissions
            var roleOpt = roleService.getRole(member.roleId());

            if (roleOpt.isEmpty()) {
                log.error("Role not found: '{}'", member.roleId());
                return new HashSet<>();
            }

            var permissions = roleOpt.get().permissions();

            log.debug("Loaded '{}' workspace permissions for user: '{}'", permissions.size(), userId);

            return permissions;

        } catch (Exception e) {
            log.error("Failed to load workspace permissions for user: '{}', workspace: '{}'",
                    userId, workspaceId, e);
            return new HashSet<>();
        }
    }

    /**
     * Get user's project permissions
     *
     * Loads permissions from database by querying project membership and role.
     *
     * @param userId the user ID
     * @param projectId the project ID
     * @return set of permission names
     */
    public Set<String> getUserProjectPermissions(String userId, String projectId) {
        log.debug("Loading project permissions for user: '{}', project: '{}'", userId, projectId);

        try {
            // Get project member info
            var memberOpt = projectMemberService.getMember(projectId, userId);

            if (memberOpt.isEmpty()) {
                log.debug("User '{}' is not a member of project '{}'", userId, projectId);
                return new HashSet<>();
            }

            var member = memberOpt.get();

            // Check member status
            if (member.status() != MemberStatus.ACTIVE) {
                log.debug("User '{}' membership in project '{}' is not active: '{}'",
                        userId, projectId, member.status());
                return new HashSet<>();
            }

            // Get role permissions
            var roleOpt = roleService.getRole(member.roleId());

            if (roleOpt.isEmpty()) {
                log.error("Role not found: '{}'", member.roleId());
                return new HashSet<>();
            }

            var permissions = roleOpt.get().permissions();

            log.debug("Loaded '{}' project permissions for user: '{}'", permissions.size(), userId);

            return permissions;

        } catch (Exception e) {
            log.error("Failed to load project permissions for user: '{}', project: '{}'",
                    userId, projectId, e);
            return new HashSet<>();
        }
    }

    /**
     * Check if user permissions satisfy requirements
     *
     * @param userPermissions user's permission set
     * @param requiredPermissions required permissions array
     * @param requireAll true for AND logic, false for OR logic
     * @return true if requirements are met
     */
    private boolean checkPermissions(Set<String> userPermissions, String[] requiredPermissions, boolean requireAll) {
        Set<String> required = Arrays.stream(requiredPermissions).collect(Collectors.toSet());

        if (requireAll) {
            // AND logic: user must have ALL required permissions
            return userPermissions.containsAll(required);
        } else {
            // OR logic: user must have ANY required permission
            return required.stream().anyMatch(userPermissions::contains);
        }
    }

    /**
     * Invalidate permission cache for user in workspace
     *
     * Call this when user's workspace membership or role changes
     *
     * @param userId the user ID
     * @param workspaceId the workspace ID
     */
    public void invalidateWorkspaceCache(String userId, String workspaceId) {
        log.info("Invalidating workspace permission cache: user='{}', workspace='{}'", userId, workspaceId);
        cacheService.invalidateWorkspacePermissions(userId, workspaceId);
    }

    /**
     * Invalidate permission cache for user in project
     *
     * Call this when user's project membership or role changes
     *
     * @param userId the user ID
     * @param projectId the project ID
     */
    public void invalidateProjectCache(String userId, String projectId) {
        log.info("Invalidating project permission cache: user='{}', project='{}'", userId, projectId);
        cacheService.invalidateProjectPermissions(userId, projectId);
    }

    /**
     * Invalidate all permission caches for a workspace
     *
     * Call this when workspace role definitions change
     *
     * @param workspaceId the workspace ID
     */
    public void invalidateAllWorkspaceCache(String workspaceId) {
        log.info("Invalidating all permission caches for workspace: '{}'", workspaceId);
        cacheService.invalidateAllWorkspacePermissions(workspaceId);
    }
}
