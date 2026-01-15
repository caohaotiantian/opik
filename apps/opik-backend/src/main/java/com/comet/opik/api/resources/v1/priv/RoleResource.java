package com.comet.opik.api.resources.v1.priv;

import com.codahale.metrics.annotation.Timed;
import com.comet.opik.api.RoleScope;
import com.comet.opik.api.error.ErrorMessage;
import com.comet.opik.domain.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

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
    private final RoleService roleService;

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

    @GET
    @Operation(operationId = "listRoles", summary = "List available roles", description = "Get list of available roles, optionally filtered by scope. Scopes: system, workspace, project", responses = {
            @ApiResponse(responseCode = "200", description = "Role list", content = @Content(schema = @Schema(implementation = RoleListResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response listRoles(@QueryParam("scope") String scope) {
        log.info("Listing roles with scope filter: '{}'", scope);

        List<com.comet.opik.api.Role> roles;

        if (StringUtils.isNotBlank(scope)) {
            RoleScope roleScope;
            try {
                roleScope = RoleScope.valueOf(scope.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid role scope: " + scope);
            }
            roles = roleService.getRolesByScope(roleScope);
        } else {
            roles = roleService.getBuiltinRoles();
        }

        List<Role> filteredRoles = roles.stream()
                .map(role -> new Role(
                        role.id(),
                        role.name(),
                        role.displayName(),
                        role.description(),
                        role.scope().name().toLowerCase(),
                        role.builtin(),
                        new ArrayList<>(role.permissions())))
                .toList();

        log.info("Found '{}' roles", filteredRoles.size());

        return Response.ok()
                .entity(new RoleListResponse(filteredRoles, filteredRoles.size()))
                .build();
    }
}
