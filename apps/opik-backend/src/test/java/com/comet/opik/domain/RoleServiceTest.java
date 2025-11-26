package com.comet.opik.domain;

import com.comet.opik.api.Role;
import com.comet.opik.api.RoleScope;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色服务单元测试
 *
 * 测试范围：
 * - 角色查询（by ID, by name/scope）
 * - 内置角色查询
 * - 按scope查询角色
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoleService单元测试")
class RoleServiceTest {

    @Mock
    private RoleDAO roleDAO;

    private RoleService roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleService(roleDAO);
    }

    @Nested
    @DisplayName("角色查询测试")
    class GetRoleTests {

        @Test
        @DisplayName("应该通过ID成功获取角色")
        void shouldGetRoleByIdSuccessfully() {
            // Given
            String roleId = UUID.randomUUID().toString();
            Role role = Role.builder()
                    .id(roleId)
                    .name("workspace_admin")
                    .scope(RoleScope.WORKSPACE)
                    .builtin(true)
                    .build();

            when(roleDAO.findById(roleId)).thenReturn(Optional.of(role));

            // When
            Optional<Role> result = roleService.getRole(roleId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(role);
            verify(roleDAO).findById(roleId);
        }

        @Test
        @DisplayName("角色不存在时应该返回空")
        void shouldReturnEmptyWhenRoleNotFound() {
            // Given
            String roleId = UUID.randomUUID().toString();
            when(roleDAO.findById(roleId)).thenReturn(Optional.empty());

            // When
            Optional<Role> result = roleService.getRole(roleId);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("按名称和范围查询角色测试")
    class GetRoleByNameAndScopeTests {

        @Test
        @DisplayName("应该通过名称和范围成功获取角色")
        void shouldGetRoleByNameAndScopeSuccessfully() {
            // Given
            String roleName = "workspace_admin";
            RoleScope scope = RoleScope.WORKSPACE;

            Role role = Role.builder()
                    .id(UUID.randomUUID().toString())
                    .name(roleName)
                    .scope(scope)
                    .builtin(true)
                    .build();

            when(roleDAO.findByNameAndScope(roleName, scope)).thenReturn(Optional.of(role));

            // When
            Optional<Role> result = roleService.getRoleByNameAndScope(roleName, scope);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo(roleName);
            assertThat(result.get().scope()).isEqualTo(scope);
        }

        @Test
        @DisplayName("角色不存在时应该返回空")
        void shouldReturnEmptyWhenRoleNotFoundByNameAndScope() {
            // Given
            String roleName = "nonexistent_role";
            RoleScope scope = RoleScope.WORKSPACE;

            when(roleDAO.findByNameAndScope(roleName, scope)).thenReturn(Optional.empty());

            // When
            Optional<Role> result = roleService.getRoleByNameAndScope(roleName, scope);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("内置角色查询测试")
    class GetBuiltinRolesTests {

        @Test
        @DisplayName("应该获取所有内置角色")
        void shouldGetAllBuiltinRoles() {
            // Given
            List<Role> builtinRoles = List.of(
                    Role.builder().id(UUID.randomUUID().toString()).name("system_admin")
                            .scope(RoleScope.SYSTEM).builtin(true).build(),
                    Role.builder().id(UUID.randomUUID().toString()).name("workspace_admin")
                            .scope(RoleScope.WORKSPACE).builtin(true).build(),
                    Role.builder().id(UUID.randomUUID().toString()).name("project_admin")
                            .scope(RoleScope.PROJECT).builtin(true).build());

            when(roleDAO.findBuiltinRoles()).thenReturn(builtinRoles);

            // When
            List<Role> result = roleService.getBuiltinRoles();

            // Then
            assertThat(result).hasSize(3);
            assertThat(result).allMatch(Role::builtin);
            assertThat(result.stream().map(Role::name))
                    .containsExactlyInAnyOrder("system_admin", "workspace_admin", "project_admin");
        }

        @Test
        @DisplayName("没有内置角色时应该返回空列表")
        void shouldReturnEmptyListWhenNoBuiltinRoles() {
            // Given
            when(roleDAO.findBuiltinRoles()).thenReturn(List.of());

            // When
            List<Role> result = roleService.getBuiltinRoles();

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("应该通过名称获取内置角色")
        void shouldGetBuiltinRoleByName() {
            // Given
            String roleName = "workspace_admin";
            RoleScope scope = RoleScope.WORKSPACE;

            Role role = Role.builder()
                    .id(UUID.randomUUID().toString())
                    .name(roleName)
                    .scope(scope)
                    .builtin(true)
                    .build();

            when(roleDAO.findByNameAndScope(roleName, scope)).thenReturn(Optional.of(role));

            // When
            Role result = roleService.getBuiltinRole(roleName, scope);

            // Then
            assertThat(result.name()).isEqualTo(roleName);
            assertThat(result.scope()).isEqualTo(scope);
            assertThat(result.builtin()).isTrue();
        }

        @Test
        @DisplayName("内置角色不存在时应该抛出异常")
        void shouldThrowExceptionWhenBuiltinRoleNotFound() {
            // Given
            String roleName = "nonexistent_role";
            RoleScope scope = RoleScope.WORKSPACE;

            when(roleDAO.findByNameAndScope(roleName, scope)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> roleService.getBuiltinRole(roleName, scope))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Builtin role not found");
        }
    }

    @Nested
    @DisplayName("按范围查询角色测试")
    class GetRolesByScopeTests {

        @Test
        @DisplayName("应该获取所有系统级角色")
        void shouldGetSystemScopedRoles() {
            // Given
            List<Role> systemRoles = List.of(
                    Role.builder().id(UUID.randomUUID().toString()).name("system_admin")
                            .scope(RoleScope.SYSTEM).builtin(true).build(),
                    Role.builder().id(UUID.randomUUID().toString()).name("system_operator")
                            .scope(RoleScope.SYSTEM).builtin(true).build());

            when(roleDAO.findByScope(RoleScope.SYSTEM)).thenReturn(systemRoles);

            // When
            List<Role> result = roleService.getRolesByScope(RoleScope.SYSTEM);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(role -> role.scope() == RoleScope.SYSTEM);
        }

        @Test
        @DisplayName("应该获取所有工作空间级角色")
        void shouldGetWorkspaceScopedRoles() {
            // Given
            List<Role> workspaceRoles = List.of(
                    Role.builder().id(UUID.randomUUID().toString()).name("workspace_admin")
                            .scope(RoleScope.WORKSPACE).builtin(true).build(),
                    Role.builder().id(UUID.randomUUID().toString()).name("workspace_developer")
                            .scope(RoleScope.WORKSPACE).builtin(true).build(),
                    Role.builder().id(UUID.randomUUID().toString()).name("workspace_viewer")
                            .scope(RoleScope.WORKSPACE).builtin(true).build());

            when(roleDAO.findByScope(RoleScope.WORKSPACE)).thenReturn(workspaceRoles);

            // When
            List<Role> result = roleService.getRolesByScope(RoleScope.WORKSPACE);

            // Then
            assertThat(result).hasSize(3);
            assertThat(result).allMatch(role -> role.scope() == RoleScope.WORKSPACE);
        }

        @Test
        @DisplayName("应该获取所有项目级角色")
        void shouldGetProjectScopedRoles() {
            // Given
            List<Role> projectRoles = List.of(
                    Role.builder().id(UUID.randomUUID().toString()).name("project_admin")
                            .scope(RoleScope.PROJECT).builtin(true).build(),
                    Role.builder().id(UUID.randomUUID().toString()).name("project_contributor")
                            .scope(RoleScope.PROJECT).builtin(true).build(),
                    Role.builder().id(UUID.randomUUID().toString()).name("project_viewer")
                            .scope(RoleScope.PROJECT).builtin(true).build());

            when(roleDAO.findByScope(RoleScope.PROJECT)).thenReturn(projectRoles);

            // When
            List<Role> result = roleService.getRolesByScope(RoleScope.PROJECT);

            // Then
            assertThat(result).hasSize(3);
            assertThat(result).allMatch(role -> role.scope() == RoleScope.PROJECT);
        }

        @Test
        @DisplayName("没有指定范围的角色时应该返回空列表")
        void shouldReturnEmptyListWhenNoRolesForScope() {
            // Given
            when(roleDAO.findByScope(RoleScope.SYSTEM)).thenReturn(List.of());

            // When
            List<Role> result = roleService.getRolesByScope(RoleScope.SYSTEM);

            // Then
            assertThat(result).isEmpty();
        }
    }
}
