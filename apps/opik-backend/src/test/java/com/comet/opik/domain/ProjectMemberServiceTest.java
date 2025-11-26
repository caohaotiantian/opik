package com.comet.opik.domain;

import com.comet.opik.api.MemberStatus;
import com.comet.opik.api.ProjectMember;
import com.comet.opik.api.Role;
import com.comet.opik.api.RoleScope;
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
 * 项目成员服务单元测试
 *
 * 测试范围：
 * - 成员添加
 * - 成员查询
 * - 成员角色更新
 * - 成员状态更新
 * - 成员移除
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectMemberService单元测试")
class ProjectMemberServiceTest {

    @Mock
    private ProjectMemberDAO memberDAO;

    @Mock
    private RoleService roleService;

    @Mock
    private IdGenerator idGenerator;

    private ProjectMemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new ProjectMemberService(memberDAO, roleService, idGenerator);
    }

    @Nested
    @DisplayName("添加成员测试")
    class AddMemberTests {

        @Test
        @DisplayName("应该成功添加成员到项目")
        void shouldAddMemberSuccessfully() {
            // Given
            String projectId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String roleId = UUID.randomUUID().toString();
            String addedBy = "admin";
            String memberId = UUID.randomUUID().toString();

            Role projectRole = Role.builder()
                    .id(roleId)
                    .name("project_contributor")
                    .scope(RoleScope.PROJECT)
                    .build();

            when(memberDAO.exists(projectId, userId)).thenReturn(false);
            when(roleService.getRole(roleId)).thenReturn(Optional.of(projectRole));
            when(idGenerator.generateId()).thenReturn(UUID.fromString(memberId));

            // When
            ProjectMember result = memberService.addMember(projectId, userId, roleId, addedBy);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(memberId);
            assertThat(result.projectId()).isEqualTo(projectId);
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.roleId()).isEqualTo(roleId);
            assertThat(result.status()).isEqualTo(MemberStatus.ACTIVE);

            verify(memberDAO).exists(projectId, userId);
            verify(roleService).getRole(roleId);
            verify(memberDAO).insert(any(ProjectMember.class));
        }

        @Test
        @DisplayName("应该拒绝重复添加成员")
        void shouldRejectDuplicateMember() {
            // Given
            String projectId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String roleId = UUID.randomUUID().toString();

            when(memberDAO.exists(projectId, userId)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> memberService.addMember(projectId, userId, roleId, "admin"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already a member");

            verify(memberDAO).exists(projectId, userId);
            verify(memberDAO, never()).insert(any());
        }

        @Test
        @DisplayName("应该拒绝不存在的角色")
        void shouldRejectNonExistentRole() {
            // Given
            String projectId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String roleId = UUID.randomUUID().toString();

            when(memberDAO.exists(projectId, userId)).thenReturn(false);
            when(roleService.getRole(roleId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> memberService.addMember(projectId, userId, roleId, "admin"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Role not found");

            verify(memberDAO, never()).insert(any());
        }

        @Test
        @DisplayName("应该拒绝非项目范围的角色")
        void shouldRejectNonProjectScopedRole() {
            // Given
            String projectId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String roleId = UUID.randomUUID().toString();

            Role workspaceRole = Role.builder()
                    .id(roleId)
                    .name("workspace_admin")
                    .scope(RoleScope.WORKSPACE)
                    .build();

            when(memberDAO.exists(projectId, userId)).thenReturn(false);
            when(roleService.getRole(roleId)).thenReturn(Optional.of(workspaceRole));

            // When & Then
            assertThatThrownBy(() -> memberService.addMember(projectId, userId, roleId, "admin"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Role must be project-scoped");

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
            String projectId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();

            ProjectMember member = ProjectMember.builder()
                    .id(UUID.randomUUID().toString())
                    .projectId(projectId)
                    .userId(userId)
                    .roleId(UUID.randomUUID().toString())
                    .status(MemberStatus.ACTIVE)
                    .build();

            when(memberDAO.findByProjectAndUser(projectId, userId)).thenReturn(Optional.of(member));

            // When
            Optional<ProjectMember> result = memberService.getMember(projectId, userId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(member);
        }

        @Test
        @DisplayName("应该获取项目的所有成员")
        void shouldGetProjectMembers() {
            // Given
            String projectId = UUID.randomUUID().toString();
            List<ProjectMember> members = List.of(
                    ProjectMember.builder().id(UUID.randomUUID().toString()).userId("user1").build(),
                    ProjectMember.builder().id(UUID.randomUUID().toString()).userId("user2").build());

            when(memberDAO.findByProject(projectId)).thenReturn(members);

            // When
            List<ProjectMember> result = memberService.getProjectMembers(projectId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).isEqualTo(members);
        }

        @Test
        @DisplayName("应该获取用户的所有项目")
        void shouldGetUserProjects() {
            // Given
            String userId = UUID.randomUUID().toString();
            List<ProjectMember> projects = List.of(
                    ProjectMember.builder().id(UUID.randomUUID().toString()).projectId("proj1").build(),
                    ProjectMember.builder().id(UUID.randomUUID().toString()).projectId("proj2").build());

            when(memberDAO.findByUser(userId)).thenReturn(projects);

            // When
            List<ProjectMember> result = memberService.getUserProjects(userId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).isEqualTo(projects);
        }
    }

    @Nested
    @DisplayName("更新成员角色测试")
    class UpdateMemberRoleTests {

        @Test
        @DisplayName("应该成功更新成员角色")
        void shouldUpdateMemberRoleSuccessfully() {
            // Given
            String projectId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String oldRoleId = UUID.randomUUID().toString();
            String newRoleId = UUID.randomUUID().toString();
            String updatedBy = "admin";

            ProjectMember existingMember = ProjectMember.builder()
                    .id(UUID.randomUUID().toString())
                    .projectId(projectId)
                    .userId(userId)
                    .roleId(oldRoleId)
                    .status(MemberStatus.ACTIVE)
                    .build();

            Role newRole = Role.builder()
                    .id(newRoleId)
                    .name("project_admin")
                    .scope(RoleScope.PROJECT)
                    .build();

            when(memberDAO.findByProjectAndUser(projectId, userId)).thenReturn(Optional.of(existingMember));
            when(roleService.getRole(newRoleId)).thenReturn(Optional.of(newRole));

            // When
            memberService.updateMemberRole(projectId, userId, newRoleId, updatedBy);

            // Then
            verify(memberDAO).updateRole(projectId, userId, newRoleId, updatedBy);
        }

        @Test
        @DisplayName("成员不存在时应该抛出异常")
        void shouldThrowExceptionWhenMemberNotFound() {
            // Given
            String projectId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String roleId = UUID.randomUUID().toString();

            when(memberDAO.findByProjectAndUser(projectId, userId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> memberService.updateMemberRole(projectId, userId, roleId, "admin"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not a member");

            verify(memberDAO, never()).updateRole(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("应该拒绝不存在的角色")
        void shouldRejectNonExistentRole() {
            // Given
            String projectId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String roleId = UUID.randomUUID().toString();

            ProjectMember member = ProjectMember.builder()
                    .id(UUID.randomUUID().toString())
                    .projectId(projectId)
                    .userId(userId)
                    .build();

            when(memberDAO.findByProjectAndUser(projectId, userId)).thenReturn(Optional.of(member));
            when(roleService.getRole(roleId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> memberService.updateMemberRole(projectId, userId, roleId, "admin"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Role not found");

            verify(memberDAO, never()).updateRole(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("应该拒绝非项目范围的角色")
        void shouldRejectNonProjectScopedRole() {
            // Given
            String projectId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String roleId = UUID.randomUUID().toString();

            ProjectMember member = ProjectMember.builder()
                    .id(UUID.randomUUID().toString())
                    .projectId(projectId)
                    .userId(userId)
                    .build();

            Role systemRole = Role.builder()
                    .id(roleId)
                    .name("system_admin")
                    .scope(RoleScope.SYSTEM)
                    .build();

            when(memberDAO.findByProjectAndUser(projectId, userId)).thenReturn(Optional.of(member));
            when(roleService.getRole(roleId)).thenReturn(Optional.of(systemRole));

            // When & Then
            assertThatThrownBy(() -> memberService.updateMemberRole(projectId, userId, roleId, "admin"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Role must be project-scoped");

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
            String projectId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            MemberStatus newStatus = MemberStatus.SUSPENDED;
            String updatedBy = "admin";

            ProjectMember member = ProjectMember.builder()
                    .id(UUID.randomUUID().toString())
                    .projectId(projectId)
                    .userId(userId)
                    .status(MemberStatus.ACTIVE)
                    .build();

            when(memberDAO.findByProjectAndUser(projectId, userId)).thenReturn(Optional.of(member));

            // When
            memberService.updateMemberStatus(projectId, userId, newStatus, updatedBy);

            // Then
            verify(memberDAO).updateStatus(projectId, userId, newStatus, updatedBy);
        }

        @Test
        @DisplayName("成员不存在时应该抛出异常")
        void shouldThrowExceptionWhenMemberNotFoundOnStatusUpdate() {
            // Given
            String projectId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();

            when(memberDAO.findByProjectAndUser(projectId, userId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(
                    () -> memberService.updateMemberStatus(projectId, userId, MemberStatus.SUSPENDED, "admin"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not a member");

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
            String projectId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();

            ProjectMember member = ProjectMember.builder()
                    .id(UUID.randomUUID().toString())
                    .projectId(projectId)
                    .userId(userId)
                    .status(MemberStatus.ACTIVE)
                    .build();

            when(memberDAO.findByProjectAndUser(projectId, userId)).thenReturn(Optional.of(member));

            // When
            memberService.removeMember(projectId, userId);

            // Then
            verify(memberDAO).delete(projectId, userId);
        }

        @Test
        @DisplayName("成员不存在时应该抛出异常")
        void shouldThrowExceptionWhenMemberNotFoundOnRemove() {
            // Given
            String projectId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();

            when(memberDAO.findByProjectAndUser(projectId, userId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> memberService.removeMember(projectId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not a member");

            verify(memberDAO, never()).delete(anyString(), anyString());
        }
    }
}
