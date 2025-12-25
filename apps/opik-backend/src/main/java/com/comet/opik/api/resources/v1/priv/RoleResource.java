package com.comet.opik.api.resources.v1.priv;

import com.codahale.metrics.annotation.Timed;
import com.comet.opik.api.error.ErrorMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色管理 API
 *
 * 基于三级 RBAC 权限模型:
 * 1. 系统级 (System Level) - 系统管理员
 * 2. 工作空间级 (Workspace Level) - 工作空间管理员、开发者、查看者
 * 3. 项目级 (Project Level) - 项目管理员、项目贡献者、项目查看者
 *
 * 权限继承规则:
 * - 系统管理员 → 自动拥有所有工作空间和项目的权限
 * - 工作空间管理员 → 自动拥有工作空间内所有项目的权限
 * - 项目成员 → 仅拥有分配项目的权限
 */
@Path("/v1/private/roles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Tag(name = "Roles", description = "Role management endpoints")
public class RoleResource {

    /**
     * 角色定义
     */
    public record Role(
            String id,
            String name,
            String displayName,
            String description,
            String scope,
            boolean isSystem,
            List<String> permissions) {
    }

    /**
     * 角色列表响应
     */
    public record RoleListResponse(List<Role> content, int total) {
    }

    // ==================== 预定义角色（按照 RBAC 设计文档） ====================

    private static final List<Role> PREDEFINED_ROLES = List.of(

            // ==================== 系统级角色 ====================

            /**
             * 系统管理员
             * - 管理所有用户
             * - 管理所有工作空间
             * - 系统配置
             * - 查看全局审计日志
             * - 自动拥有所有工作空间和项目的权限
             */
            new Role(
                    "system-admin",
                    "system_admin",
                    "System Admin",
                    "系统管理员，拥有所有权限",
                    "system",
                    true,
                    List.of(
                            "system:admin",
                            "system:users:manage",
                            "system:workspaces:manage",
                            "system:settings",
                            "system:audit:view")),

            // ==================== 工作空间级角色 ====================

            /**
             * 工作空间管理员
             * - 工作空间设置
             * - 成员管理
             * - 所有资源的 CRUD 权限
             * - API Key 管理
             * - 不能访问其他工作空间
             * - 不能修改系统设置
             */
            new Role(
                    "workspace-admin",
                    "workspace_admin",
                    "Workspace Admin",
                    "工作空间管理员，管理工作空间内的所有资源",
                    "workspace",
                    true,
                    List.of(
                            "workspace:admin",
                            "workspace:view",
                            "workspace:settings",
                            "workspace:members:manage",
                            "project:create", "project:view", "project:update", "project:delete",
                            "trace:create", "trace:view", "trace:update", "trace:delete",
                            "dataset:create", "dataset:view", "dataset:update", "dataset:delete",
                            "prompt:create", "prompt:view", "prompt:update", "prompt:delete",
                            "experiment:create", "experiment:view", "experiment:update", "experiment:delete",
                            "apikey:create", "apikey:view", "apikey:revoke",
                            "feedback:definition:create", "feedback:definition:view",
                            "feedback:definition:update", "feedback:definition:delete")),

            /**
             * 开发者
             * - 创建和管理项目
             * - 创建和管理 Traces, Datasets, Prompts
             * - 创建和查看自己的 API Key
             * - 不能管理成员
             * - 不能修改工作空间设置
             * - 不能撤销他人的 API Key
             */
            new Role(
                    "developer",
                    "developer",
                    "Developer",
                    "开发者，可以创建和管理项目、数据集等资源",
                    "workspace",
                    true,
                    List.of(
                            "workspace:view",
                            "project:create", "project:view", "project:update", "project:delete",
                            "trace:create", "trace:view", "trace:update", "trace:delete",
                            "dataset:create", "dataset:view", "dataset:update", "dataset:delete",
                            "prompt:create", "prompt:view", "prompt:update", "prompt:delete",
                            "experiment:create", "experiment:view", "experiment:update", "experiment:delete",
                            "apikey:create", "apikey:view",
                            "feedback:definition:view")),

            /**
             * 查看者
             * - 查看所有资源
             * - 不能创建、修改、删除任何资源
             * - 不能管理成员
             * - 不能创建 API Key
             */
            new Role(
                    "viewer",
                    "viewer",
                    "Viewer",
                    "查看者，只能查看资源",
                    "workspace",
                    true,
                    List.of(
                            "workspace:view",
                            "project:view",
                            "trace:view",
                            "dataset:view",
                            "prompt:view",
                            "experiment:view",
                            "feedback:definition:view")),

            // ==================== 项目级角色 ====================

            /**
             * 项目管理员
             * - 项目设置
             * - 管理项目内的 Traces
             * - 不能删除项目本身（需要工作空间权限）
             * - 不能访问项目外的资源
             */
            new Role(
                    "project-admin",
                    "project_admin",
                    "Project Admin",
                    "项目管理员，管理项目内的所有资源",
                    "project",
                    true,
                    List.of(
                            "project:view", "project:update", "project:delete",
                            "trace:create", "trace:view", "trace:update", "trace:delete")),

            /**
             * 项目贡献者
             * - 创建和编辑 Traces
             * - 不能删除 Traces
             * - 不能修改项目设置
             */
            new Role(
                    "project-contributor",
                    "project_contributor",
                    "Project Contributor",
                    "项目贡献者，可以创建和编辑 Traces",
                    "project",
                    true,
                    List.of(
                            "project:view",
                            "trace:create", "trace:view", "trace:update")),

            /**
             * 项目查看者
             * - 查看项目和 Traces
             * - 不能修改任何内容
             */
            new Role(
                    "project-viewer",
                    "project_viewer",
                    "Project Viewer",
                    "项目查看者，只能查看项目和 Traces",
                    "project",
                    true,
                    List.of(
                            "project:view",
                            "trace:view")));

    @GET
    @Operation(operationId = "listRoles", summary = "List available roles", description = "Get list of available roles, optionally filtered by scope. Scopes: system, workspace, project", responses = {
            @ApiResponse(responseCode = "200", description = "Role list", content = @Content(schema = @Schema(implementation = RoleListResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response listRoles(@QueryParam("scope") String scope) {
        log.info("Listing roles with scope filter: '{}'", scope);

        List<Role> filteredRoles;

        if (scope != null && !scope.isEmpty()) {
            filteredRoles = PREDEFINED_ROLES.stream()
                    .filter(role -> role.scope().equalsIgnoreCase(scope))
                    .toList();
        } else {
            filteredRoles = new ArrayList<>(PREDEFINED_ROLES);
        }

        log.info("Found '{}' roles", filteredRoles.size());

        return Response.ok()
                .entity(new RoleListResponse(filteredRoles, filteredRoles.size()))
                .build();
    }
}
