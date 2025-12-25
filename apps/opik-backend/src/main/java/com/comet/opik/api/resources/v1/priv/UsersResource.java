package com.comet.opik.api.resources.v1.priv;

import com.codahale.metrics.annotation.Timed;
import com.comet.opik.api.User;
import com.comet.opik.api.error.ErrorMessage;
import com.comet.opik.domain.UserService;
import com.comet.opik.infrastructure.auth.RequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Path("/v1/private/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Tag(name = "Users", description = "User listing endpoints")
public class UsersResource {

    private final @NonNull UserService userService;
    private final @NonNull Provider<RequestContext> requestContext;

    /**
     * 用户列表响应
     */
    public record UserListResponse(List<User> content, int total, int page, int size) {
    }

    @GET
    @Path("/available")
    @Operation(operationId = "listAvailableUsers", summary = "List available users", description = "Get list of all active users that can be added to workspaces", responses = {
            @ApiResponse(responseCode = "200", description = "User list", content = @Content(schema = @Schema(implementation = UserListResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response listAvailableUsers(
            @QueryParam("search") String search,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size) {

        String currentUserId = requestContext.get().getUserId();
        log.info("User '{}' listing available users, search: '{}'", currentUserId, search);

        int pageNum = page != null ? page : 1;
        int pageSize = size != null ? Math.min(size, 100) : 50;

        // 获取所有活跃用户
        List<User> users = userService.getAllUsers(search, "active", null, pageNum, pageSize);
        int total = userService.countAllUsers(search, "active", null);

        log.info("Found '{}' available users", users.size());

        return Response.ok()
                .entity(new UserListResponse(users, total, pageNum, pageSize))
                .build();
    }
}
