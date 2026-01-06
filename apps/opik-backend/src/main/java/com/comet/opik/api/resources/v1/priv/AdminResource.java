package com.comet.opik.api.resources.v1.priv;

import com.codahale.metrics.annotation.Timed;
import com.comet.opik.api.User;
import com.comet.opik.api.error.ErrorMessage;
import com.comet.opik.domain.UserService;
import com.comet.opik.infrastructure.auth.RequestContext;
import com.comet.opik.infrastructure.authorization.RequiresPermission;
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
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Path("/v1/private/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Tag(name = "Admin", description = "System administration endpoints")
public class AdminResource {

    private final @NonNull UserService userService;
    private final @NonNull Provider<RequestContext> requestContext;

    /**
     * 用户列表响应
     */
    public record UserListResponse(List<User> content, int total, int page, int size) {
    }

    /**
     * 创建用户请求
     */
    public record CreateUserRequest(
            @Schema(description = "Username", example = "john_doe", required = true) String username,
            @Schema(description = "Email", example = "john@example.com", required = true) String email,
            @Schema(description = "Password", example = "SecurePass123!", required = true) String password,
            @Schema(description = "Full name", example = "John Doe") String fullName,
            @Schema(description = "System admin flag", example = "false") Boolean systemAdmin) {
    }

    /**
     * 更新用户请求
     */
    public record UpdateUserRequest(
            @Schema(description = "Email", example = "john@example.com") String email,
            @Schema(description = "Full name", example = "John Doe") String fullName,
            @Schema(description = "Avatar URL") String avatarUrl,
            @Schema(description = "Locale", example = "zh-CN") String locale) {
    }

    /**
     * 用户状态更新请求
     */
    public record UpdateUserStatusRequest(
            @Schema(description = "New user status", example = "active") String status) {
    }

    @GET
    @Path("/users")
    @RequiresPermission(systemAdminOnly = true)
    @Operation(operationId = "listAllUsers", summary = "List all users", description = "Get paginated list of all users (admin only)", responses = {
            @ApiResponse(responseCode = "200", description = "User list", content = @Content(schema = @Schema(implementation = UserListResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response listAllUsers(
            @QueryParam("search") String search,
            @QueryParam("status") String status,
            @QueryParam("systemAdmin") Boolean systemAdmin,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size) {

        String currentUserId = requestContext.get().getUserId();
        log.info("Admin user '{}' listing all users", currentUserId);

        // Permission check is handled by @RequiresPermission annotation

        int pageNum = page != null ? page : 1;
        int pageSize = size != null ? Math.min(size, 100) : 20;

        List<User> users = userService.getAllUsers(search, status, systemAdmin, pageNum, pageSize);
        int total = userService.countAllUsers(search, status, systemAdmin);

        log.info("Found '{}' users (total: '{}')", users.size(), total);

        return Response.ok()
                .entity(new UserListResponse(users, total, pageNum, pageSize))
                .build();
    }

    @PUT
    @Path("/users/{userId}/status")
    @RequiresPermission(systemAdminOnly = true)
    @Operation(operationId = "updateUserStatus", summary = "Update user status", description = "Update a user's status (admin only)", responses = {
            @ApiResponse(responseCode = "200", description = "User status updated", content = @Content(schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response updateUserStatus(
            @PathParam("userId") String userId,
            @RequestBody(content = @Content(schema = @Schema(implementation = UpdateUserStatusRequest.class))) @Valid UpdateUserStatusRequest request) {

        String currentUserId = requestContext.get().getUserId();
        log.info("Admin user '{}' updating status of user '{}'", currentUserId, userId);

        // Permission check is handled by @RequiresPermission annotation

        // Prevent admin from deactivating themselves
        if (userId.equals(currentUserId) && !"active".equalsIgnoreCase(request.status())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorMessage(List.of("Cannot deactivate your own account")))
                    .build();
        }

        User updatedUser = userService.updateUserStatus(userId, request.status(), currentUserId);

        log.info("User '{}' status updated to '{}'", userId, request.status());

        return Response.ok().entity(updatedUser).build();
    }

    @POST
    @Path("/users")
    @RequiresPermission(systemAdminOnly = true)
    @Operation(operationId = "createUser", summary = "Create user", description = "Create a new user (admin only)", responses = {
            @ApiResponse(responseCode = "201", description = "User created", content = @Content(schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "409", description = "Username or email already exists", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response createUser(
            @RequestBody(content = @Content(schema = @Schema(implementation = CreateUserRequest.class))) @Valid CreateUserRequest request) {

        String currentUserId = requestContext.get().getUserId();
        log.info("Admin user '{}' creating new user: '{}'", currentUserId, request.username());

        // Permission check is handled by @RequiresPermission annotation

        // Register user
        User newUser = userService.registerUser(
                request.username(),
                request.email(),
                request.password(),
                request.fullName());

        // Set system admin flag if requested
        if (request.systemAdmin() != null && request.systemAdmin()) {
            userService.setSystemAdmin(newUser.id(), true, currentUserId);
            newUser = userService.getUser(newUser.id()).orElseThrow();
        }

        log.info("Admin user '{}' created new user: '{}'", currentUserId, newUser.id());

        return Response.status(Response.Status.CREATED)
                .entity(newUser)
                .build();
    }

    @PUT
    @Path("/users/{userId}")
    @RequiresPermission(systemAdminOnly = true)
    @Operation(operationId = "updateUser", summary = "Update user", description = "Update user information (admin only)", responses = {
            @ApiResponse(responseCode = "200", description = "User updated", content = @Content(schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "409", description = "Email already in use", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response updateUser(
            @PathParam("userId") String userId,
            @RequestBody(content = @Content(schema = @Schema(implementation = UpdateUserRequest.class))) @Valid UpdateUserRequest request) {

        String currentUserId = requestContext.get().getUserId();
        log.info("Admin user '{}' updating user: '{}'", currentUserId, userId);

        // Permission check is handled by @RequiresPermission annotation

        User updatedUser = userService.updateUser(
                userId,
                request.email(),
                request.fullName(),
                request.avatarUrl(),
                request.locale(),
                currentUserId);

        log.info("Admin user '{}' updated user: '{}'", currentUserId, userId);

        return Response.ok().entity(updatedUser).build();
    }

    @DELETE
    @Path("/users/{userId}")
    @RequiresPermission(systemAdminOnly = true)
    @Operation(operationId = "deleteUser", summary = "Delete user", description = "Delete a user (soft delete, admin only)", responses = {
            @ApiResponse(responseCode = "204", description = "User deleted"),
            @ApiResponse(responseCode = "400", description = "Cannot delete your own account", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response deleteUser(@PathParam("userId") String userId) {

        String currentUserId = requestContext.get().getUserId();
        log.info("Admin user '{}' deleting user: '{}'", currentUserId, userId);

        // Permission check is handled by @RequiresPermission annotation

        // Prevent admin from deleting themselves
        if (userId.equals(currentUserId)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorMessage(List.of("Cannot delete your own account")))
                    .build();
        }

        userService.deleteUser(userId, currentUserId);

        log.info("Admin user '{}' deleted user: '{}'", currentUserId, userId);

        return Response.noContent().build();
    }
}
