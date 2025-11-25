package com.comet.opik.domain;

import com.comet.opik.api.MemberStatus;
import com.comet.opik.api.ProjectMember;
import com.comet.opik.api.RoleScope;
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
public class ProjectMemberService {

    private final @NonNull ProjectMemberDAO memberDAO;
    private final @NonNull RoleService roleService;
    private final @NonNull IdGenerator idGenerator;

    /**
     * Add a member to a project
     *
     * @param projectId the project ID
     * @param userId the user ID
     * @param roleId the role ID
     * @param addedBy the user who added the member
     * @return the created project member
     */
    public ProjectMember addMember(String projectId, String userId, String roleId, String addedBy) {
        log.info("Adding user '{}' to project '{}' with role '{}'", userId, projectId, roleId);

        // Check if already a member
        if (memberDAO.exists(projectId, userId)) {
            log.warn("User '{}' is already a member of project '{}'", userId, projectId);
            throw new BadRequestException(
                    "User '%s' is already a member of project '%s'".formatted(userId, projectId));
        }

        // Validate role exists and is project-scoped
        var role = roleService.getRole(roleId)
                .orElseThrow(() -> new NotFoundException("Role not found: '%s'".formatted(roleId)));

        if (role.scope() != RoleScope.PROJECT) {
            throw new BadRequestException("Role must be project-scoped, got: '%s'".formatted(role.scope()));
        }

        Instant now = Instant.now();

        // Create member
        var member = ProjectMember.builder()
                .id(idGenerator.generateId().toString())
                .projectId(projectId)
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

        log.info("User '{}' added to project '{}' successfully", userId, projectId);

        return member;
    }

    /**
     * Get member by project and user
     *
     * @param projectId the project ID
     * @param userId the user ID
     * @return the project member
     */
    public Optional<ProjectMember> getMember(String projectId, String userId) {
        return memberDAO.findByProjectAndUser(projectId, userId);
    }

    /**
     * Get all members of a project
     *
     * @param projectId the project ID
     * @return list of project members
     */
    public List<ProjectMember> getProjectMembers(String projectId) {
        return memberDAO.findByProject(projectId);
    }

    /**
     * Get all projects a user is a member of
     *
     * @param userId the user ID
     * @return list of project members
     */
    public List<ProjectMember> getUserProjects(String userId) {
        return memberDAO.findByUser(userId);
    }

    /**
     * Update member role
     *
     * @param projectId the project ID
     * @param userId the user ID
     * @param roleId the new role ID
     * @param updatedBy the user who made the update
     */
    public void updateMemberRole(String projectId, String userId, String roleId, String updatedBy) {
        log.info("Updating role for user '{}' in project '{}' to '{}'", userId, projectId, roleId);

        // Check member exists
        var member = getMember(projectId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "User '%s' is not a member of project '%s'".formatted(userId, projectId)));

        // Validate role
        var role = roleService.getRole(roleId)
                .orElseThrow(() -> new NotFoundException("Role not found: '%s'".formatted(roleId)));

        if (role.scope() != RoleScope.PROJECT) {
            throw new BadRequestException("Role must be project-scoped, got: '%s'".formatted(role.scope()));
        }

        memberDAO.updateRole(projectId, userId, roleId, updatedBy);

        log.info("Role updated successfully for user '{}' in project '{}'", userId, projectId);
    }

    /**
     * Update member status
     *
     * @param projectId the project ID
     * @param userId the user ID
     * @param status the new status
     * @param updatedBy the user who made the update
     */
    public void updateMemberStatus(String projectId, String userId, MemberStatus status, String updatedBy) {
        log.info("Updating status for user '{}' in project '{}' to '{}'", userId, projectId, status);

        // Check member exists
        var member = getMember(projectId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "User '%s' is not a member of project '%s'".formatted(userId, projectId)));

        memberDAO.updateStatus(projectId, userId, status, updatedBy);

        log.info("Status updated successfully for user '{}' in project '{}'", userId, projectId);
    }

    /**
     * Remove member from project
     *
     * @param projectId the project ID
     * @param userId the user ID
     */
    public void removeMember(String projectId, String userId) {
        log.info("Removing user '{}' from project '{}'", userId, projectId);

        // Check member exists
        var member = getMember(projectId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "User '%s' is not a member of project '%s'".formatted(userId, projectId)));

        memberDAO.delete(projectId, userId);

        log.info("User '{}' removed from project '{}' successfully", userId, projectId);
    }
}
