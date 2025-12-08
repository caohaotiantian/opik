package com.comet.opik.domain;

import com.comet.opik.api.MemberStatus;
import com.comet.opik.api.RoleScope;
import com.comet.opik.api.Workspace;
import com.comet.opik.api.WorkspaceMember;
import com.comet.opik.api.WorkspaceStatus;
import com.comet.opik.api.error.ConflictException;
import com.comet.opik.infrastructure.audit.Auditable;
import com.comet.opik.infrastructure.audit.Operation;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.guicey.jdbi3.tx.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.comet.opik.infrastructure.db.TransactionTemplateAsync.WRITE;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class WorkspaceService {

    private final @NonNull WorkspaceDAO workspaceDAO;
    private final @NonNull WorkspaceMemberService memberService;
    private final @NonNull RoleService roleService;
    private final @NonNull UserService userService;
    private final @NonNull IdGenerator idGenerator;
    private final @NonNull TransactionTemplate transactionTemplate;

    private static final int DEFAULT_QUOTA = 10;
    private static final int DEFAULT_MAX_MEMBERS = 100;

    /**
     * Create a new workspace
     *
     * @param name the workspace name (unique)
     * @param displayName the display name
     * @param description the description (optional)
     * @param ownerUserId the owner user ID
     * @return the created workspace
     */
    @Auditable(action = "Create Workspace", resourceType = "workspace", operation = Operation.CREATE)
    public Workspace createWorkspace(String name, String displayName, String description, String ownerUserId) {
        log.info("Creating workspace: '{}' by user: '{}'", name, ownerUserId);

        // Validate workspace name uniqueness
        if (workspaceDAO.existsByName(name)) {
            throw new ConflictException("Workspace name already exists: '%s'".formatted(name));
        }

        // Validate owner exists
        userService.getUser(ownerUserId)
                .orElseThrow(() -> new NotFoundException("User not found: '%s'".formatted(ownerUserId)));

        Instant now = Instant.now();

        // Create workspace
        var workspace = Workspace.builder()
                .id(idGenerator.generateId().toString())
                .name(name)
                .displayName(displayName)
                .description(description)
                .status(WorkspaceStatus.ACTIVE)
                .ownerUserId(ownerUserId)
                .quotaLimit(DEFAULT_QUOTA)
                .allowPublicAccess(false)
                .maxMembers(DEFAULT_MAX_MEMBERS)
                .version(0)
                .createdAt(now)
                .createdBy(ownerUserId)
                .lastUpdatedAt(now)
                .lastUpdatedBy(ownerUserId)
                .build();

        // Create workspace and add owner as admin (within write transaction)
        transactionTemplate.inTransaction(WRITE, handle -> {
            var wDao = handle.attach(WorkspaceDAO.class);
            var wmDao = handle.attach(WorkspaceMemberDAO.class);

            wDao.insert(workspace);

            // Add owner as Workspace Admin
            var workspaceAdminRole = roleService.getBuiltinRole("Workspace Admin", RoleScope.WORKSPACE);
            var member = WorkspaceMember.builder()
                    .id(idGenerator.generateId().toString())
                    .workspaceId(workspace.id())
                    .userId(ownerUserId)
                    .roleId(workspaceAdminRole.id())
                    .status(MemberStatus.ACTIVE)
                    .version(0)
                    .createdAt(now)
                    .createdBy("system")
                    .lastUpdatedAt(now)
                    .lastUpdatedBy("system")
                    .build();
            wmDao.insert(member);

            return null;
        });

        log.info("Workspace '{}' created successfully", workspace.name());

        return workspace;
    }

    /**
     * Get workspace by ID
     *
     * @param workspaceId the workspace ID
     * @return the workspace
     */
    public Optional<Workspace> getWorkspace(String workspaceId) {
        return workspaceDAO.findById(workspaceId);
    }

    /**
     * Get workspace by name
     *
     * @param name the workspace name
     * @return the workspace
     */
    public Optional<Workspace> getWorkspaceByName(String name) {
        return workspaceDAO.findByName(name);
    }

    /**
     * Get workspaces for a user
     *
     * @param userId the user ID
     * @return list of workspaces
     */
    public List<Workspace> getUserWorkspaces(String userId) {
        return workspaceDAO.findByUserId(userId);
    }

    /**
     * Update workspace
     *
     * @param workspaceId the workspace ID
     * @param name the new name (optional)
     * @param displayName the new display name (optional)
     * @param description the new description (optional)
     * @param quotaLimit the new quota limit (optional)
     * @param maxMembers the new max members (optional)
     * @param settings the new settings JSON (optional)
     * @param updatedBy the user who made the update
     * @return the updated workspace
     */
    @Auditable(action = "Update Workspace", resourceType = "workspace", operation = Operation.UPDATE, logChanges = true)
    public Workspace updateWorkspace(String workspaceId, String name, String displayName, String description,
            Integer quotaLimit, Integer maxMembers, String settings, String updatedBy) {
        log.info("Updating workspace: '{}'", workspaceId);

        var workspace = getWorkspace(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace not found: '%s'".formatted(workspaceId)));

        // Check name uniqueness if changed
        if (name != null && !name.equals(workspace.name())) {
            if (workspaceDAO.existsByName(name)) {
                throw new ConflictException("Workspace name already exists: '%s'".formatted(name));
            }
        }

        workspaceDAO.update(workspaceId,
                name != null ? name : workspace.name(),
                displayName != null ? displayName : workspace.displayName(),
                description != null ? description : workspace.description(),
                quotaLimit != null ? quotaLimit : workspace.quotaLimit(),
                maxMembers != null ? maxMembers : workspace.maxMembers(),
                settings != null ? settings : workspace.settings(),
                updatedBy);

        log.info("Workspace '{}' updated successfully", workspaceId);

        return getWorkspace(workspaceId).orElseThrow();
    }

    /**
     * Update workspace status
     *
     * @param workspaceId the workspace ID
     * @param status the new status
     * @param updatedBy the user who made the update
     */
    public void updateWorkspaceStatus(String workspaceId, WorkspaceStatus status, String updatedBy) {
        log.info("Updating workspace status: '{}' to '{}'", workspaceId, status);

        var workspace = getWorkspace(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace not found: '%s'".formatted(workspaceId)));

        workspaceDAO.updateStatus(workspaceId, status, updatedBy);

        log.info("Workspace '{}' status updated to '{}'", workspace.name(), status);
    }

    /**
     * Delete workspace (soft delete by setting status to DELETED)
     *
     * @param workspaceId the workspace ID
     * @param deletedBy the user who made the deletion
     */
    @Auditable(action = "Delete Workspace", resourceType = "workspace", operation = Operation.DELETE)
    public void deleteWorkspace(String workspaceId, String deletedBy) {
        log.info("Deleting workspace: '{}'", workspaceId);

        updateWorkspaceStatus(workspaceId, WorkspaceStatus.DELETED, deletedBy);

        log.info("Workspace '{}' deleted successfully", workspaceId);
    }
}
