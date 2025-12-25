package com.comet.opik.api.resources.v1.priv;

import com.codahale.metrics.annotation.Timed;
import com.comet.opik.api.Workspace;
import com.comet.opik.api.WorkspaceCreateRequest;
import com.comet.opik.api.WorkspaceMember;
import com.comet.opik.api.WorkspaceMemberAddRequest;
import com.comet.opik.api.WorkspaceMemberResponse;
import com.comet.opik.api.WorkspaceMemberUpdateRequest;
import com.comet.opik.api.WorkspaceUpdateRequest;
import com.comet.opik.api.error.ErrorMessage;
import com.comet.opik.domain.RoleService;
import com.comet.opik.domain.UserService;
import com.comet.opik.domain.WorkspaceMemberService;
import com.comet.opik.domain.WorkspaceService;
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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Path("/v1/private/workspaces-management")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Tag(name = "Workspace Management", description = "Workspace CRUD and member management")
public class WorkspaceManagementResource {

    private final @NonNull WorkspaceService workspaceService;
    private final @NonNull WorkspaceMemberService memberService;
    private final @NonNull UserService userService;
    private final @NonNull RoleService roleService;
    private final @NonNull Provider<RequestContext> requestContext;

    @GET
    @Operation(operationId = "listWorkspaces", summary = "List my workspaces", description = "Get list of workspaces the current user has access to", responses = {
            @ApiResponse(responseCode = "200", description = "Workspace list", content = @Content(schema = @Schema(implementation = Workspace.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response listWorkspaces() {
        String userId = requestContext.get().getUserId();

        log.info("Listing workspaces for user '{}'", userId);

        List<Workspace> workspaces = workspaceService.getUserWorkspaces(userId);

        log.info("Found '{}' workspaces for user '{}'", workspaces.size(), userId);

        return Response.ok().entity(workspaces).build();
    }

    @POST
    @RequiresPermission("WORKSPACE_CREATE")
    @Operation(operationId = "createWorkspace", summary = "Create workspace", description = "Create a new workspace", responses = {
            @ApiResponse(responseCode = "201", description = "Workspace created", content = @Content(schema = @Schema(implementation = Workspace.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "409", description = "Workspace name already exists", content = @Content(schema = @Schema(implementation = io.dropwizard.jersey.errors.ErrorMessage.class)))
    })
    public Response createWorkspace(
            @RequestBody(content = @Content(schema = @Schema(implementation = WorkspaceCreateRequest.class))) @Valid WorkspaceCreateRequest request,
            @Context UriInfo uriInfo) {

        String userId = requestContext.get().getUserId();

        log.info("Creating workspace '{}' by user '{}'", request.name(), userId);

        Workspace workspace = workspaceService.createWorkspace(
                request.name(),
                request.displayName() != null ? request.displayName() : request.name(),
                request.description(),
                userId);

        log.info("Workspace '{}' created successfully", workspace.name());

        return Response.created(uriInfo.getAbsolutePathBuilder().path(workspace.id()).build())
                .entity(workspace)
                .build();
    }

    @GET
    @Path("/{id}")
    @RequiresPermission("WORKSPACE_VIEW")
    @Operation(operationId = "getWorkspace", summary = "Get workspace", description = "Get workspace details by ID", responses = {
            @ApiResponse(responseCode = "200", description = "Workspace details", content = @Content(schema = @Schema(implementation = Workspace.class))),
            @ApiResponse(responseCode = "404", description = "Workspace not found", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response getWorkspace(@PathParam("id") String id) {
        log.info("Getting workspace '{}'", id);

        Workspace workspace = workspaceService.getWorkspace(id)
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException("Workspace not found: " + id));

        log.info("Retrieved workspace '{}'", workspace.name());

        return Response.ok().entity(workspace).build();
    }

    @PUT
    @Path("/{id}")
    @RequiresPermission("WORKSPACE_SETTINGS")
    @Operation(operationId = "updateWorkspace", summary = "Update workspace", description = "Update workspace details", responses = {
            @ApiResponse(responseCode = "200", description = "Workspace updated", content = @Content(schema = @Schema(implementation = Workspace.class))),
            @ApiResponse(responseCode = "404", description = "Workspace not found", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "409", description = "Workspace name already exists", content = @Content(schema = @Schema(implementation = io.dropwizard.jersey.errors.ErrorMessage.class)))
    })
    public Response updateWorkspace(
            @PathParam("id") String id,
            @RequestBody(content = @Content(schema = @Schema(implementation = WorkspaceUpdateRequest.class))) @Valid WorkspaceUpdateRequest request) {

        String userId = requestContext.get().getUserId();

        log.info("Updating workspace '{}'", id);

        Workspace workspace = workspaceService.updateWorkspace(
                id,
                request.name(),
                request.displayName(),
                request.description(),
                request.quotaLimit(),
                request.maxMembers(),
                null,
                userId);

        log.info("Workspace '{}' updated successfully", workspace.name());

        return Response.ok().entity(workspace).build();
    }

    @DELETE
    @Path("/{id}")
    @RequiresPermission("WORKSPACE_ADMIN")
    @Operation(operationId = "deleteWorkspace", summary = "Delete workspace", description = "Delete workspace (soft delete)", responses = {
            @ApiResponse(responseCode = "204", description = "Workspace deleted"),
            @ApiResponse(responseCode = "404", description = "Workspace not found", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response deleteWorkspace(@PathParam("id") String id) {
        String userId = requestContext.get().getUserId();

        log.info("Deleting workspace '{}'", id);

        workspaceService.deleteWorkspace(id, userId);

        log.info("Workspace '{}' deleted successfully", id);

        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/members")
    @RequiresPermission("WORKSPACE_MEMBER_VIEW")
    @Operation(operationId = "listMembers", summary = "List workspace members", description = "Get list of workspace members with user details", responses = {
            @ApiResponse(responseCode = "200", description = "Member list", content = @Content(schema = @Schema(implementation = WorkspaceMemberResponse.class))),
            @ApiResponse(responseCode = "404", description = "Workspace not found", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response listMembers(@PathParam("id") String workspaceId) {
        log.info("Listing members for workspace '{}'", workspaceId);

        List<WorkspaceMember> members = memberService.getWorkspaceMembers(workspaceId);

        // Convert to response with user details
        List<WorkspaceMemberResponse> memberResponses = members.stream()
                .map(member -> {
                    var user = userService.getUser(member.userId()).orElse(null);
                    var role = roleService.getRole(member.roleId()).orElse(null);
                    return WorkspaceMemberResponse.from(member, user, role);
                })
                .toList();

        log.info("Found '{}' members for workspace '{}'", memberResponses.size(), workspaceId);

        return Response.ok().entity(memberResponses).build();
    }

    @POST
    @Path("/{id}/members")
    @RequiresPermission("WORKSPACE_MEMBER_MANAGE")
    @Operation(operationId = "addMember", summary = "Add workspace member", description = "Add a user as workspace member", responses = {
            @ApiResponse(responseCode = "201", description = "Member added"),
            @ApiResponse(responseCode = "409", description = "User already a member", content = @Content(schema = @Schema(implementation = io.dropwizard.jersey.errors.ErrorMessage.class)))
    })
    public Response addMember(
            @PathParam("id") String workspaceId,
            @RequestBody(content = @Content(schema = @Schema(implementation = WorkspaceMemberAddRequest.class))) @Valid WorkspaceMemberAddRequest request) {

        String addedBy = requestContext.get().getUserId();

        log.info("Adding member '{}' to workspace '{}'", request.userId(), workspaceId);

        memberService.addMember(workspaceId, request.userId(), request.roleId(), addedBy);

        log.info("Member '{}' added to workspace '{}'", request.userId(), workspaceId);

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @Path("/{id}/members/{userId}")
    @RequiresPermission("WORKSPACE_MEMBER_MANAGE")
    @Operation(operationId = "updateMember", summary = "Update member role", description = "Update workspace member's role", responses = {
            @ApiResponse(responseCode = "204", description = "Member role updated"),
            @ApiResponse(responseCode = "404", description = "Member not found", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response updateMember(
            @PathParam("id") String workspaceId,
            @PathParam("userId") String userId,
            @RequestBody(content = @Content(schema = @Schema(implementation = WorkspaceMemberUpdateRequest.class))) @Valid WorkspaceMemberUpdateRequest request) {

        String updatedBy = requestContext.get().getUserId();

        log.info("Updating member '{}' role in workspace '{}'", userId, workspaceId);

        memberService.updateMemberRole(workspaceId, userId, request.roleId(), updatedBy);

        log.info("Member '{}' role updated in workspace '{}'", userId, workspaceId);

        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}/members/{userId}")
    @RequiresPermission("WORKSPACE_MEMBER_MANAGE")
    @Operation(operationId = "removeMember", summary = "Remove member", description = "Remove user from workspace", responses = {
            @ApiResponse(responseCode = "204", description = "Member removed"),
            @ApiResponse(responseCode = "404", description = "Member not found", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response removeMember(
            @PathParam("id") String workspaceId,
            @PathParam("userId") String userId) {

        log.info("Removing member '{}' from workspace '{}'", userId, workspaceId);

        memberService.removeMember(workspaceId, userId);

        log.info("Member '{}' removed from workspace '{}'", userId, workspaceId);

        return Response.noContent().build();
    }
}
