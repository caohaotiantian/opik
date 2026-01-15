package com.comet.opik.domain;

import com.comet.opik.api.MemberStatus;
import com.comet.opik.api.RoleScope;
import com.comet.opik.api.WorkspaceMember;
import com.comet.opik.api.error.ConflictException;
import com.comet.opik.infrastructure.audit.Auditable;
import com.comet.opik.infrastructure.audit.Operation;
import com.comet.opik.infrastructure.authorization.PermissionCacheService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class WorkspaceMemberService {

    private final @NonNull WorkspaceMemberDAO memberDAO;
    private final @NonNull RoleService roleService;
    private final @NonNull IdGenerator idGenerator;
    private final @NonNull PermissionCacheService permissionCacheService;

    /**
     * Add a member to a workspace
     *
     * @param workspaceId the workspace ID
     * @param userId the user ID
     * @param roleId the role ID
     * @param addedBy the user who added the member
     * @return the created workspace member
     */
    @Auditable(action = "Add Workspace Member", resourceType = "workspace_member", operation = Operation.CREATE)
    public WorkspaceMember addMember(String workspaceId, String userId, String roleId, String addedBy) {
        log.info("Adding user '{}' to workspace '{}' with role '{}'", userId, workspaceId, roleId);

        // Check if already a member
        if (memberDAO.exists(workspaceId, userId)) {
            throw new ConflictException(
                    "User '%s' is already a member of workspace '%s'".formatted(userId, workspaceId));
        }

        // Validate role exists and is workspace-scoped
        var role = roleService.getRole(roleId)
                .orElseThrow(() -> new NotFoundException("Role not found: '%s'".formatted(roleId)));

        if (role.scope() != RoleScope.WORKSPACE) {
            throw new BadRequestException("Role must be workspace-scoped, got: '%s'".formatted(role.scope()));
        }

        Instant now = Instant.now();

        // Create member
        var member = WorkspaceMember.builder()
                .id(idGenerator.generateId().toString())
                .workspaceId(workspaceId)
                .userId(userId)
                .roleId(roleId)
                .status(MemberStatus.ACTIVE)
                .version(0)
                .createdAt(now)
                .createdBy(addedBy)
                .lastUpdatedAt(now)
                .lastUpdatedBy(addedBy)
                .build();

        memberDAO.insert(member);

        // Invalidate permission cache for the new member
        permissionCacheService.invalidateWorkspacePermissions(userId, workspaceId);
        log.info("User '{}' added to workspace '{}' successfully", userId, workspaceId);

        return member;
    }

    /**
     * Get member by workspace and user
     *
     * @param workspaceId the workspace ID
     * @param userId the user ID
     * @return the workspace member
     */
    public Optional<WorkspaceMember> getMember(String workspaceId, String userId) {
        return memberDAO.findByWorkspaceAndUser(workspaceId, userId);
    }

    /**
     * Get all members of a workspace
     *
     * @param workspaceId the workspace ID
     * @return list of workspace members
     */
    public List<WorkspaceMember> getWorkspaceMembers(String workspaceId) {
        return memberDAO.findByWorkspace(workspaceId);
    }

    /**
     * Get all workspaces a user is a member of
     *
     * @param userId the user ID
     * @return list of workspace members
     */
    public List<WorkspaceMember> getUserWorkspaces(String userId) {
        return memberDAO.findByUser(userId);
    }

    /**
     * Update member role
     *
     * @param workspaceId the workspace ID
     * @param userId the user ID
     * @param roleId the new role ID
     * @param updatedBy the user who made the update
     */
    @Auditable(action = "Update Workspace Member Role", resourceType = "workspace_member", operation = Operation.UPDATE)
    public void updateMemberRole(String workspaceId, String userId, String roleId, String updatedBy) {
        log.info("Updating role for user '{}' in workspace '{}' to '{}'", userId, workspaceId, roleId);

        // Check member exists
        var member = getMember(workspaceId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "User '%s' is not a member of workspace '%s'".formatted(userId, workspaceId)));

        // Validate role
        var role = roleService.getRole(roleId)
                .orElseThrow(() -> new NotFoundException("Role not found: '%s'".formatted(roleId)));

        if (role.scope() != RoleScope.WORKSPACE) {
            throw new BadRequestException("Role must be workspace-scoped, got: '%s'".formatted(role.scope()));
        }

        memberDAO.updateRole(workspaceId, userId, roleId, updatedBy);

        // Invalidate permission cache since role has changed
        permissionCacheService.invalidateWorkspacePermissions(userId, workspaceId);
        log.info("Role updated successfully for user '{}' in workspace '{}'", userId, workspaceId);
    }

    /**
     * Update member status
     *
     * @param workspaceId the workspace ID
     * @param userId the user ID
     * @param status the new status
     * @param updatedBy the user who made the update
     */
    public void updateMemberStatus(String workspaceId, String userId, MemberStatus status, String updatedBy) {
        log.info("Updating status for user '{}' in workspace '{}' to '{}'", userId, workspaceId, status);

        // Check member exists
        var member = getMember(workspaceId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "User '%s' is not a member of workspace '%s'".formatted(userId, workspaceId)));

        memberDAO.updateStatus(workspaceId, userId, status, updatedBy);

        // Invalidate permission cache since status has changed (inactive users may lose permissions)
        permissionCacheService.invalidateWorkspacePermissions(userId, workspaceId);
        log.info("Status updated successfully for user '{}' in workspace '{}'", userId, workspaceId);
    }

    /**
     * Remove member from workspace
     *
     * @param workspaceId the workspace ID
     * @param userId the user ID
     */
    @Auditable(action = "Remove Workspace Member", resourceType = "workspace_member", operation = Operation.DELETE)
    public void removeMember(String workspaceId, String userId) {
        log.info("Removing user '{}' from workspace '{}'", userId, workspaceId);

        // Check member exists
        var member = getMember(workspaceId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "User '%s' is not a member of workspace '%s'".formatted(userId, workspaceId)));

        memberDAO.delete(workspaceId, userId);

        // Invalidate permission cache for the removed member
        permissionCacheService.invalidateWorkspacePermissions(userId, workspaceId);
        log.info("User '{}' removed from workspace '{}' successfully", userId, workspaceId);
    }
}
