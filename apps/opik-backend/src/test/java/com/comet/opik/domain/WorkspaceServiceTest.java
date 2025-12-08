package com.comet.opik.domain;

import com.comet.opik.api.Role;
import com.comet.opik.api.RoleScope;
import com.comet.opik.api.Workspace;
import com.comet.opik.api.WorkspaceStatus;
import com.comet.opik.api.error.ConflictException;
import jakarta.ws.rs.NotFoundException;
import org.jdbi.v3.core.Handle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.vyarus.guicey.jdbi3.tx.TxAction;
import ru.vyarus.guicey.jdbi3.tx.TxConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作空间服务单元测试
 *
 * 测试范围：
 * - 工作空间创建（含自动添加owner为admin）
 * - 工作空间查询
 * - 工作空间更新
 * - 工作空间删除
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkspaceService单元测试")
class WorkspaceServiceTest {

    @Mock
    private WorkspaceDAO workspaceDAO;

    @Mock
    private WorkspaceMemberDAO workspaceMemberDAO;

    @Mock
    private WorkspaceMemberService memberService;

    @Mock
    private RoleService roleService;

    @Mock
    private UserService userService;

    @Mock
    private IdGenerator idGenerator;

    @Mock
    private ru.vyarus.guicey.jdbi3.tx.TransactionTemplate transactionTemplate;

    @Mock
    private Handle handle;

    private WorkspaceService workspaceService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        workspaceService = new WorkspaceService(
                workspaceDAO,
                memberService,
                roleService,
                userService,
                idGenerator,
                transactionTemplate);

        // Mock TransactionTemplate to execute the action with our mocked handle
        lenient().when(transactionTemplate.inTransaction(any(TxConfig.class), any(TxAction.class)))
                .thenAnswer(invocation -> {
                    TxAction<Object> action = invocation.getArgument(1);
                    lenient().when(handle.attach(WorkspaceDAO.class)).thenReturn(workspaceDAO);
                    lenient().when(handle.attach(WorkspaceMemberDAO.class)).thenReturn(workspaceMemberDAO);
                    return action.execute(handle);
                });
    }

    @Nested
    @DisplayName("工作空间创建测试")
    class CreateWorkspaceTests {

        @Test
        @DisplayName("应该成功创建工作空间")
        void shouldCreateWorkspaceSuccessfully() {
            // Given
            String name = "test-workspace";
            String displayName = "Test Workspace";
            String description = "Test Description";
            String ownerUserId = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();
            String memberId = UUID.randomUUID().toString();
            String roleId = UUID.randomUUID().toString();

            when(workspaceDAO.existsByName(name)).thenReturn(false);
            when(userService.getUser(ownerUserId)).thenReturn(Optional.of(
                    com.comet.opik.api.User.builder()
                            .id(ownerUserId)
                            .username("testuser")
                            .build()));
            // idGenerator is called twice: once for workspace, once for member
            when(idGenerator.generateId())
                    .thenReturn(UUID.fromString(workspaceId))
                    .thenReturn(UUID.fromString(memberId));

            Role workspaceAdminRole = Role.builder()
                    .id(roleId)
                    .name("Workspace Admin")
                    .scope(RoleScope.WORKSPACE)
                    .build();
            when(roleService.getBuiltinRole("Workspace Admin", RoleScope.WORKSPACE))
                    .thenReturn(workspaceAdminRole);

            // When
            Workspace result = workspaceService.createWorkspace(name, displayName, description, ownerUserId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(workspaceId);
            assertThat(result.name()).isEqualTo(name);
            assertThat(result.displayName()).isEqualTo(displayName);
            assertThat(result.description()).isEqualTo(description);
            assertThat(result.ownerUserId()).isEqualTo(ownerUserId);
            assertThat(result.status()).isEqualTo(WorkspaceStatus.ACTIVE);
            assertThat(result.allowPublicAccess()).isFalse();

            // Verify interactions
            verify(workspaceDAO).existsByName(name);
            verify(userService).getUser(ownerUserId);
            verify(workspaceDAO).insert(any(Workspace.class));
            verify(workspaceMemberDAO).insert(any(com.comet.opik.api.WorkspaceMember.class));
        }

        @Test
        @DisplayName("应该拒绝重复的工作空间名称")
        void shouldRejectDuplicateWorkspaceName() {
            // Given
            String name = "existing-workspace";
            when(workspaceDAO.existsByName(name)).thenReturn(true);

            // When & Then
            assertThatThrownBy(
                    () -> workspaceService.createWorkspace(name, "Display Name", null, "user123"))
                    .isInstanceOf(ConflictException.class);

            verify(workspaceDAO).existsByName(name);
            verify(workspaceDAO, never()).insert(any());
        }

        @Test
        @DisplayName("应该拒绝不存在的用户")
        void shouldRejectNonExistentOwner() {
            // Given
            String name = "test-workspace";
            String nonExistentUserId = UUID.randomUUID().toString();

            when(workspaceDAO.existsByName(name)).thenReturn(false);
            when(userService.getUser(nonExistentUserId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(
                    () -> workspaceService.createWorkspace(name, "Display Name", null, nonExistentUserId))
                    .isInstanceOf(NotFoundException.class);

            verify(userService).getUser(nonExistentUserId);
            verify(workspaceDAO, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("工作空间查询测试")
    class GetWorkspaceTests {

        @Test
        @DisplayName("应该成功通过ID获取工作空间")
        void shouldGetWorkspaceById() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            Workspace workspace = Workspace.builder()
                    .id(workspaceId)
                    .name("test-workspace")
                    .displayName("Test Workspace")
                    .status(WorkspaceStatus.ACTIVE)
                    .build();

            when(workspaceDAO.findById(workspaceId)).thenReturn(Optional.of(workspace));

            // When
            Optional<Workspace> result = workspaceService.getWorkspace(workspaceId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(workspace);
            verify(workspaceDAO).findById(workspaceId);
        }

        @Test
        @DisplayName("工作空间不存在时应该返回空")
        void shouldReturnEmptyWhenWorkspaceNotFound() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            when(workspaceDAO.findById(workspaceId)).thenReturn(Optional.empty());

            // When
            Optional<Workspace> result = workspaceService.getWorkspace(workspaceId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("应该成功通过名称获取工作空间")
        void shouldGetWorkspaceByName() {
            // Given
            String name = "test-workspace";
            Workspace workspace = Workspace.builder()
                    .id(UUID.randomUUID().toString())
                    .name(name)
                    .displayName("Test Workspace")
                    .build();

            when(workspaceDAO.findByName(name)).thenReturn(Optional.of(workspace));

            // When
            Optional<Workspace> result = workspaceService.getWorkspaceByName(name);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo(name);
        }

        @Test
        @DisplayName("应该成功获取用户的工作空间列表")
        void shouldGetUserWorkspaces() {
            // Given
            String userId = UUID.randomUUID().toString();
            List<Workspace> workspaces = List.of(
                    Workspace.builder().id(UUID.randomUUID().toString()).name("workspace1").build(),
                    Workspace.builder().id(UUID.randomUUID().toString()).name("workspace2").build());

            when(workspaceDAO.findByUserId(userId)).thenReturn(workspaces);

            // When
            List<Workspace> result = workspaceService.getUserWorkspaces(userId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).isEqualTo(workspaces);
        }
    }

    @Nested
    @DisplayName("工作空间更新测试")
    class UpdateWorkspaceTests {

        @Test
        @DisplayName("应该成功更新工作空间")
        void shouldUpdateWorkspaceSuccessfully() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            String newDisplayName = "Updated Display Name";
            String newDescription = "Updated Description";
            Integer newQuotaLimit = 20;
            String updatedBy = "admin";

            Workspace existingWorkspace = Workspace.builder()
                    .id(workspaceId)
                    .name("test-workspace")
                    .displayName("Old Display Name")
                    .description("Old Description")
                    .quotaLimit(10)
                    .build();

            Workspace updatedWorkspace = existingWorkspace.toBuilder()
                    .displayName(newDisplayName)
                    .description(newDescription)
                    .quotaLimit(newQuotaLimit)
                    .build();

            when(workspaceDAO.findById(workspaceId))
                    .thenReturn(Optional.of(existingWorkspace))
                    .thenReturn(Optional.of(updatedWorkspace));

            // When
            Workspace result = workspaceService.updateWorkspace(
                    workspaceId, null, newDisplayName, newDescription, newQuotaLimit, null, null, updatedBy);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.displayName()).isEqualTo(newDisplayName);
            assertThat(result.description()).isEqualTo(newDescription);
            assertThat(result.quotaLimit()).isEqualTo(newQuotaLimit);

            verify(workspaceDAO).update(
                    eq(workspaceId),
                    eq(existingWorkspace.name()),
                    eq(newDisplayName),
                    eq(newDescription),
                    eq(newQuotaLimit),
                    any(),
                    any(),
                    eq(updatedBy));
        }

        @Test
        @DisplayName("应该拒绝更新为已存在的名称")
        void shouldRejectDuplicateNameOnUpdate() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            String existingName = "existing-name";

            Workspace workspace = Workspace.builder()
                    .id(workspaceId)
                    .name("old-name")
                    .displayName("Display Name")
                    .build();

            when(workspaceDAO.findById(workspaceId)).thenReturn(Optional.of(workspace));
            when(workspaceDAO.existsByName(existingName)).thenReturn(true);

            // When & Then
            assertThatThrownBy(
                    () -> workspaceService.updateWorkspace(workspaceId, existingName, null, null, null, null,
                            null, "admin"))
                    .isInstanceOf(ConflictException.class);

            verify(workspaceDAO, never()).update(anyString(), anyString(), anyString(), anyString(), any(),
                    any(), anyString(), anyString());
        }

        @Test
        @DisplayName("工作空间不存在时应该抛出异常")
        void shouldThrowExceptionWhenWorkspaceNotFoundOnUpdate() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            when(workspaceDAO.findById(workspaceId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(
                    () -> workspaceService.updateWorkspace(workspaceId, null, "New Name", null, null, null,
                            null, "admin"))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("工作空间状态更新测试")
    class UpdateWorkspaceStatusTests {

        @Test
        @DisplayName("应该成功更新工作空间状态")
        void shouldUpdateWorkspaceStatusSuccessfully() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            WorkspaceStatus newStatus = WorkspaceStatus.SUSPENDED;
            String updatedBy = "admin";

            Workspace workspace = Workspace.builder()
                    .id(workspaceId)
                    .name("test-workspace")
                    .status(WorkspaceStatus.ACTIVE)
                    .build();

            when(workspaceDAO.findById(workspaceId)).thenReturn(Optional.of(workspace));

            // When
            workspaceService.updateWorkspaceStatus(workspaceId, newStatus, updatedBy);

            // Then
            verify(workspaceDAO).updateStatus(workspaceId, newStatus, updatedBy);
        }

        @Test
        @DisplayName("工作空间不存在时应该抛出异常")
        void shouldThrowExceptionWhenWorkspaceNotFoundOnStatusUpdate() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            when(workspaceDAO.findById(workspaceId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(
                    () -> workspaceService.updateWorkspaceStatus(workspaceId, WorkspaceStatus.SUSPENDED, "admin"))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("工作空间删除测试")
    class DeleteWorkspaceTests {

        @Test
        @DisplayName("应该成功软删除工作空间")
        void shouldSoftDeleteWorkspaceSuccessfully() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            String deletedBy = "admin";

            Workspace workspace = Workspace.builder()
                    .id(workspaceId)
                    .name("test-workspace")
                    .status(WorkspaceStatus.ACTIVE)
                    .build();

            when(workspaceDAO.findById(workspaceId)).thenReturn(Optional.of(workspace));

            // When
            workspaceService.deleteWorkspace(workspaceId, deletedBy);

            // Then
            verify(workspaceDAO).updateStatus(workspaceId, WorkspaceStatus.DELETED, deletedBy);
        }

        @Test
        @DisplayName("工作空间不存在时应该抛出异常")
        void shouldThrowExceptionWhenWorkspaceNotFoundOnDelete() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            when(workspaceDAO.findById(workspaceId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> workspaceService.deleteWorkspace(workspaceId, "admin"))
                    .isInstanceOf(NotFoundException.class);
        }
    }
}
