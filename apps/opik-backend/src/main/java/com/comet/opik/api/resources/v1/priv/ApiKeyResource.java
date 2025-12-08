package com.comet.opik.api.resources.v1.priv;

import com.codahale.metrics.annotation.Timed;
import com.comet.opik.api.ApiKey;
import com.comet.opik.api.ApiKeyCreateRequest;
import com.comet.opik.api.error.ErrorMessage;
import com.comet.opik.domain.ApiKeyService;
import com.comet.opik.infrastructure.auth.RequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;

@Path("/v1/api-keys")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Tag(name = "API Keys", description = "API Key management resources")
public class ApiKeyResource {

    private final @NonNull ApiKeyService apiKeyService;
    private final @NonNull Provider<RequestContext> requestContext;

    @GET
    @Operation(operationId = "listApiKeys", summary = "List API keys", description = "Get list of API keys for the current user", responses = {
            @ApiResponse(responseCode = "200", description = "API key list", content = @Content(schema = @Schema(implementation = ApiKey.class)))
    })
    public Response listApiKeys() {
        String userId = requestContext.get().getUserId();
        String workspaceId = requestContext.get().getWorkspaceId();
        log.info("Listing API keys for user '{}' in workspace '{}'", userId, workspaceId);

        List<ApiKey> apiKeys = apiKeyService.listApiKeys(userId, workspaceId);
        log.info("Found '{}' API keys for user '{}'", apiKeys.size(), userId);

        return Response.ok().entity(apiKeys).build();
    }

    @POST
    @Operation(operationId = "createApiKey", summary = "Create API key", description = "Create a new API key", responses = {
            @ApiResponse(responseCode = "201", description = "API key created", content = @Content(schema = @Schema(implementation = ApiKey.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response createApiKey(@Valid ApiKeyCreateRequest request) {
        String userId = requestContext.get().getUserId();
        String workspaceId = requestContext.get().getWorkspaceId();

        log.info("Creating API key for user '{}' in workspace '{}'", userId, workspaceId);

        // Calculate expiry days from expiresAt
        Integer expiryDays = null;
        if (request.expiresAt() != null) {
            long days = java.time.Duration.between(Instant.now(), request.expiresAt()).toDays();
            expiryDays = (int) days;
        }

        // Use workspace from context (determined by Opik-Workspace header or default)
        // If request.workspaceId() is provided, it should match contextWorkspaceId
        String targetWorkspaceId = workspaceId;
        if (request.workspaceId() != null && !request.workspaceId().isEmpty()) {
            log.info("Using workspace from request: '{}'", request.workspaceId());
            targetWorkspaceId = request.workspaceId();
        }

        var apiKeyResult = apiKeyService.generateApiKey(
                userId,
                targetWorkspaceId,
                request.name(),
                request.description(),
                null, // scopes (not used yet)
                expiryDays);

        log.info("API key created successfully: '{}'", apiKeyResult.apiKey().name());

        return Response.status(Response.Status.CREATED)
                .entity(apiKeyResult)
                .build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(operationId = "revokeApiKey", summary = "Revoke API key", description = "Revoke an API key", responses = {
            @ApiResponse(responseCode = "204", description = "API key revoked"),
            @ApiResponse(responseCode = "404", description = "API key not found", content = @Content(schema = @Schema(implementation = io.dropwizard.jersey.errors.ErrorMessage.class)))
    })
    public Response revokeApiKey(@PathParam("id") String id) {
        String userId = requestContext.get().getUserId();
        log.info("Revoking API key '{}' for user '{}'", id, userId);

        apiKeyService.revokeApiKey(id, userId);

        log.info("API key '{}' revoked successfully", id);

        return Response.noContent().build();
    }
}
