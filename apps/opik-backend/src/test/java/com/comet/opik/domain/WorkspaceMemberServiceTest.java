package com.comet.opik.domain;

import com.comet.opik.api.MemberStatus;
import com.comet.opik.api.Role;
import com.comet.opik.api.RoleScope;
import com.comet.opik.api.WorkspaceMember;
import com.comet.opik.api.error.ConflictException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作空间成员服务单元测试
 *
 * 测试范围：
 * - 成员添加
 * - 成员查询
 * - 成员角色更新
 * - 成员状态更新
 * - 成员移除
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkspaceMemberService单元测试")
class WorkspaceMemberServiceTest {

    @Mock
    private WorkspaceMemberDAO memberDAO;

    @Mock
    private RoleService roleService;

    @Mock
    private IdGenerator idGenerator;

    private WorkspaceMemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new WorkspaceMemberService(memberDAO, roleService, idGenerator);
    }

    @Nested
    @DisplayName("添加成员测试")
    class AddMemberTests {

        @Test
        @DisplayName("应该成功添加成员到工作空间")
        void shouldAddMemberSuccessfully() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String roleId = UUID.randomUUID().toString();
            String addedBy = "admin";
            String memberId = UUID.randomUUID().toString();

            Role workspaceRole = Role.builder()
                    .id(roleId)
                    .name("workspace_developer")
                    .scope(RoleScope.WORKSPACE)
                    .build();

            when(memberDAO.exists(workspaceId, userId)).thenReturn(false);
            when(roleService.getRole(roleId)).thenReturn(Optional.of(workspaceRole));
            when(idGenerator.generateId()).thenReturn(UUID.fromString(memberId));

            // When
            WorkspaceMember result = memberService.addMember(workspaceId, userId, roleId, addedBy);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(memberId);
            assertThat(result.workspaceId()).isEqualTo(workspaceId);
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.roleId()).isEqualTo(roleId);
            assertThat(result.status()).isEqualTo(MemberStatus.ACTIVE);

            verify(memberDAO).exists(workspaceId, userId);
            verify(roleService).getRole(roleId);
            verify(memberDAO).insert(any(WorkspaceMember.class));
        }

        @Test
        @DisplayName("应该拒绝重复添加成员")
        void shouldRejectDuplicateMember() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String roleId = UUID.randomUUID().toString();

            when(memberDAO.exists(workspaceId, userId)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> memberService.addMember(workspaceId, userId, roleId, "admin"))
                    .isInstanceOf(ConflictException.class);

            verify(memberDAO).exists(workspaceId, userId);
            verify(memberDAO, never()).insert(any());
        }

        @Test
        @DisplayName("应该拒绝不存在的角色")
        void shouldRejectNonExistentRole() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String roleId = UUID.randomUUID().toString();

            when(memberDAO.exists(workspaceId, userId)).thenReturn(false);
            when(roleService.getRole(roleId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> memberService.addMember(workspaceId, userId, roleId, "admin"))
                    .isInstanceOf(NotFoundException.class);

            verify(memberDAO, never()).insert(any());
        }

        @Test
        @DisplayName("应该拒绝非工作空间范围的角色")
        void shouldRejectNonWorkspaceScopedRole() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String roleId = UUID.randomUUID().toString();

            Role projectRole = Role.builder()
                    .id(roleId)
                    .name("project_admin")
                    .scope(RoleScope.PROJECT)
                    .build();

            when(memberDAO.exists(workspaceId, userId)).thenReturn(false);
            when(roleService.getRole(roleId)).thenReturn(Optional.of(projectRole));

            // When & Then
            assertThatThrownBy(() -> memberService.addMember(workspaceId, userId, roleId, "admin"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Role must be workspace-scoped");

            verify(memberDAO, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("查询成员测试")
    class GetMemberTests {

        @Test
        @DisplayName("应该成功获取单个成员")
        void shouldGetMemberSuccessfully() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();

            WorkspaceMember member = WorkspaceMember.builder()
                    .id(UUID.randomUUID().toString())
                    .workspaceId(workspaceId)
                    .userId(userId)
                    .roleId(UUID.randomUUID().toString())
                    .status(MemberStatus.ACTIVE)
                    .build();

            when(memberDAO.findByWorkspaceAndUser(workspaceId, userId)).thenReturn(Optional.of(member));

            // When
            Optional<WorkspaceMember> result = memberService.getMember(workspaceId, userId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(member);
        }

        @Test
        @DisplayName("应该获取工作空间的所有成员")
        void shouldGetWorkspaceMembers() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            List<WorkspaceMember> members = List.of(
                    WorkspaceMember.builder().id(UUID.randomUUID().toString()).userId("user1").build(),
                    WorkspaceMember.builder().id(UUID.randomUUID().toString()).userId("user2").build());

            when(memberDAO.findByWorkspace(workspaceId)).thenReturn(members);

            // When
            List<WorkspaceMember> result = memberService.getWorkspaceMembers(workspaceId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).isEqualTo(members);
        }

        @Test
        @DisplayName("应该获取用户的所有工作空间")
        void shouldGetUserWorkspaces() {
            // Given
            String userId = UUID.randomUUID().toString();
            List<WorkspaceMember> workspaces = List.of(
                    WorkspaceMember.builder().id(UUID.randomUUID().toString()).workspaceId("ws1").build(),
                    WorkspaceMember.builder().id(UUID.randomUUID().toString()).workspaceId("ws2").build());

            when(memberDAO.findByUser(userId)).thenReturn(workspaces);

            // When
            List<WorkspaceMember> result = memberService.getUserWorkspaces(userId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).isEqualTo(workspaces);
        }
    }

    @Nested
    @DisplayName("更新成员角色测试")
    class UpdateMemberRoleTests {

        @Test
        @DisplayName("应该成功更新成员角色")
        void shouldUpdateMemberRoleSuccessfully() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String oldRoleId = UUID.randomUUID().toString();
            String newRoleId = UUID.randomUUID().toString();
            String updatedBy = "admin";

            WorkspaceMember existingMember = WorkspaceMember.builder()
                    .id(UUID.randomUUID().toString())
                    .workspaceId(workspaceId)
                    .userId(userId)
                    .roleId(oldRoleId)
                    .status(MemberStatus.ACTIVE)
                    .build();

            Role newRole = Role.builder()
                    .id(newRoleId)
                    .name("workspace_admin")
                    .scope(RoleScope.WORKSPACE)
                    .build();

            when(memberDAO.findByWorkspaceAndUser(workspaceId, userId)).thenReturn(Optional.of(existingMember));
            when(roleService.getRole(newRoleId)).thenReturn(Optional.of(newRole));

            // When
            memberService.updateMemberRole(workspaceId, userId, newRoleId, updatedBy);

            // Then
            verify(memberDAO).updateRole(workspaceId, userId, newRoleId, updatedBy);
        }

        @Test
        @DisplayName("成员不存在时应该抛出异常")
        void shouldThrowExceptionWhenMemberNotFound() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String roleId = UUID.randomUUID().toString();

            when(memberDAO.findByWorkspaceAndUser(workspaceId, userId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> memberService.updateMemberRole(workspaceId, userId, roleId, "admin"))
                    .isInstanceOf(NotFoundException.class);

            verify(memberDAO, never()).updateRole(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("应该拒绝不存在的角色")
        void shouldRejectNonExistentRole() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String roleId = UUID.randomUUID().toString();

            WorkspaceMember member = WorkspaceMember.builder()
                    .id(UUID.randomUUID().toString())
                    .workspaceId(workspaceId)
                    .userId(userId)
                    .build();

            when(memberDAO.findByWorkspaceAndUser(workspaceId, userId)).thenReturn(Optional.of(member));
            when(roleService.getRole(roleId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> memberService.updateMemberRole(workspaceId, userId, roleId, "admin"))
                    .isInstanceOf(NotFoundException.class);

            verify(memberDAO, never()).updateRole(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("应该拒绝非工作空间范围的角色")
        void shouldRejectNonWorkspaceScopedRole() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String roleId = UUID.randomUUID().toString();

            WorkspaceMember member = WorkspaceMember.builder()
                    .id(UUID.randomUUID().toString())
                    .workspaceId(workspaceId)
                    .userId(userId)
                    .build();

            Role systemRole = Role.builder()
                    .id(roleId)
                    .name("system_admin")
                    .scope(RoleScope.SYSTEM)
                    .build();

            when(memberDAO.findByWorkspaceAndUser(workspaceId, userId)).thenReturn(Optional.of(member));
            when(roleService.getRole(roleId)).thenReturn(Optional.of(systemRole));

            // When & Then
            assertThatThrownBy(() -> memberService.updateMemberRole(workspaceId, userId, roleId, "admin"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Role must be workspace-scoped");

            verify(memberDAO, never()).updateRole(anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("更新成员状态测试")
    class UpdateMemberStatusTests {

        @Test
        @DisplayName("应该成功更新成员状态")
        void shouldUpdateMemberStatusSuccessfully() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            MemberStatus newStatus = MemberStatus.SUSPENDED;
            String updatedBy = "admin";

            WorkspaceMember member = WorkspaceMember.builder()
                    .id(UUID.randomUUID().toString())
                    .workspaceId(workspaceId)
                    .userId(userId)
                    .status(MemberStatus.ACTIVE)
                    .build();

            when(memberDAO.findByWorkspaceAndUser(workspaceId, userId)).thenReturn(Optional.of(member));

            // When
            memberService.updateMemberStatus(workspaceId, userId, newStatus, updatedBy);

            // Then
            verify(memberDAO).updateStatus(workspaceId, userId, newStatus, updatedBy);
        }

        @Test
        @DisplayName("成员不存在时应该抛出异常")
        void shouldThrowExceptionWhenMemberNotFoundOnStatusUpdate() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();

            when(memberDAO.findByWorkspaceAndUser(workspaceId, userId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(
                    () -> memberService.updateMemberStatus(workspaceId, userId, MemberStatus.SUSPENDED, "admin"))
                    .isInstanceOf(NotFoundException.class);

            verify(memberDAO, never()).updateStatus(anyString(), anyString(), any(), anyString());
        }
    }

    @Nested
    @DisplayName("移除成员测试")
    class RemoveMemberTests {

        @Test
        @DisplayName("应该成功移除成员")
        void shouldRemoveMemberSuccessfully() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();

            WorkspaceMember member = WorkspaceMember.builder()
                    .id(UUID.randomUUID().toString())
                    .workspaceId(workspaceId)
                    .userId(userId)
                    .status(MemberStatus.ACTIVE)
                    .build();

            when(memberDAO.findByWorkspaceAndUser(workspaceId, userId)).thenReturn(Optional.of(member));

            // When
            memberService.removeMember(workspaceId, userId);

            // Then
            verify(memberDAO).delete(workspaceId, userId);
        }

        @Test
        @DisplayName("成员不存在时应该抛出异常")
        void shouldThrowExceptionWhenMemberNotFoundOnRemove() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();

            when(memberDAO.findByWorkspaceAndUser(workspaceId, userId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> memberService.removeMember(workspaceId, userId))
                    .isInstanceOf(NotFoundException.class);

            verify(memberDAO, never()).delete(anyString(), anyString());
        }
    }
}
