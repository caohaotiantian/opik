package com.comet.opik.api.resources.v1.session;

import com.codahale.metrics.annotation.Timed;
import com.comet.opik.api.CurrentUserResponse;
import com.comet.opik.api.PasswordChangeRequest;
import com.comet.opik.api.User;
import com.comet.opik.api.UserProfileUpdateRequest;
import com.comet.opik.api.Workspace;
import com.comet.opik.api.error.ErrorMessage;
import com.comet.opik.domain.PasswordService;
import com.comet.opik.domain.RoleService;
import com.comet.opik.domain.UserService;
import com.comet.opik.domain.WorkspaceMemberService;
import com.comet.opik.domain.WorkspaceService;
import com.comet.opik.infrastructure.auth.RequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Path("/v1/session")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Tag(name = "User Session", description = "User session and profile resources")
public class UserSessionResource {

    private final @NonNull UserService userService;
    private final @NonNull PasswordService passwordService;
    private final @NonNull WorkspaceService workspaceService;
    private final @NonNull WorkspaceMemberService workspaceMemberService;
    private final @NonNull RoleService roleService;
    private final @NonNull Provider<RequestContext> requestContext;

    @GET
    @Path("/current-user")
    @Operation(operationId = "getCurrentUser", summary = "Get current user", description = "Get authenticated user information with workspaces", responses = {
            @ApiResponse(responseCode = "200", description = "Current user with workspaces", content = @Content(schema = @Schema(implementation = CurrentUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response getCurrentUser() {
        String userId = requestContext.get().getUserId();

        log.info("Getting current user info for user '{}'", userId);

        User user = userService.getUser(userId)
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException("User not found: " + userId));

        // 获取用户的工作空间列表
        List<Workspace> workspaces = workspaceService.getUserWorkspaces(userId);

        // 转换为 WorkspaceInfo 并获取用户角色
        List<CurrentUserResponse.WorkspaceInfo> workspaceInfos = workspaces.stream()
                .map(ws -> {
                    // 获取用户在工作空间中的成员信息
                    String roleName = workspaceMemberService.getMember(ws.id(), userId)
                            .map(member -> roleService.getRole(member.roleId())
                                    .map(role -> role.name())
                                    .orElse("member"))
                            .orElse("owner"); // 工作空间创建者默认是 owner
                    return CurrentUserResponse.WorkspaceInfo.builder()
                            .id(ws.id())
                            .name(ws.name())
                            .displayName(ws.displayName() != null ? ws.displayName() : ws.name())
                            .role(roleName)
                            .build();
                })
                .toList();

        // 确定默认工作空间
        String defaultWorkspaceId = workspaces.isEmpty() ? null : workspaces.get(0).id();

        CurrentUserResponse response = CurrentUserResponse.builder()
                .user(user)
                .workspaces(workspaceInfos)
                .defaultWorkspaceId(defaultWorkspaceId)
                .build();

        log.info("Retrieved user info for user '{}' with '{}' workspaces", user.username(), workspaceInfos.size());

        return Response.ok().entity(response).build();
    }

    @PUT
    @Path("/profile")
    @Operation(operationId = "updateProfile", summary = "Update user profile", description = "Update authenticated user's profile information", responses = {
            @ApiResponse(responseCode = "200", description = "Profile updated", content = @Content(schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response updateProfile(
            @RequestBody(content = @Content(schema = @Schema(implementation = UserProfileUpdateRequest.class))) @Valid UserProfileUpdateRequest request) {

        String userId = requestContext.get().getUserId();

        log.info("Updating profile for user '{}'", userId);

        User updatedUser = userService.updateUser(
                userId,
                request.email(),
                request.fullName(),
                request.avatarUrl(),
                request.locale(),
                userId);

        log.info("Profile updated successfully for user '{}'", updatedUser.username());

        return Response.ok().entity(updatedUser).build();
    }

    @PUT
    @Path("/password")
    @Operation(operationId = "changePassword", summary = "Change password", description = "Change authenticated user's password", responses = {
            @ApiResponse(responseCode = "204", description = "Password changed successfully"),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response changePassword(
            @RequestBody(content = @Content(schema = @Schema(implementation = PasswordChangeRequest.class))) @Valid PasswordChangeRequest request) {

        String userId = requestContext.get().getUserId();

        log.info("Password change request for user '{}'", userId);

        userService.changePassword(userId, request.currentPassword(), request.newPassword(), userId);

        User user = userService.getUser(userId)
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException("User not found: " + userId));

        log.info("Password changed successfully for user '{}'", user.username());

        return Response.noContent().build();
    }
}
