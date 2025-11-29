package com.comet.opik.api.resources.v1.priv;

import com.codahale.metrics.annotation.Timed;
import com.comet.opik.api.ApiKey;
import com.comet.opik.api.ApiKeyCreateRequest;
import com.comet.opik.api.ApiKeyResponse;
import com.comet.opik.api.error.ErrorMessage;
import com.comet.opik.domain.ApiKeyService;
import com.comet.opik.infrastructure.auth.RequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/v1/private/api-keys")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Tag(name = "API Key Management", description = "API Key operations")
public class ApiKeyManagementResource {

    private final @NonNull ApiKeyService apiKeyService;
    private final @NonNull Provider<RequestContext> requestContext;

    @GET
    @Operation(operationId = "listApiKeys", summary = "List API keys", description = "List all API keys for a workspace (without plaintext keys)", responses = {
            @ApiResponse(responseCode = "200", description = "List of API keys", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiKeyResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response listApiKeys(@QueryParam("workspace_id") String workspaceId) {
        String userId = requestContext.get().getUserId();

        log.info("Listing API keys for user '{}' in workspace '{}'", userId, workspaceId);

        List<ApiKey> apiKeys = apiKeyService.listApiKeys(userId, workspaceId);

        List<ApiKeyResponse> response = apiKeys.stream()
                .map(apiKey -> ApiKeyResponse.builder()
                        .id(apiKey.id())
                        .name(apiKey.name())
                        .apiKey(null)
                        .workspaceId(apiKey.workspaceId())
                        .description(apiKey.description())
                        .status(apiKey.status())
                        .expiresAt(apiKey.expiresAt())
                        .createdAt(apiKey.createdAt())
                        .lastUsedAt(apiKey.lastUsedAt())
                        .build())
                .collect(Collectors.toList());

        log.info("Found '{}' API keys", response.size());

        return Response.ok(response).build();
    }

    @POST
    @Operation(operationId = "createApiKey", summary = "Create API key", description = "Create a new API key (returns plaintext key only once)", responses = {
            @ApiResponse(responseCode = "201", description = "API Key created", content = @Content(schema = @Schema(implementation = ApiKeyResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response createApiKey(
            @RequestBody(content = @Content(schema = @Schema(implementation = ApiKeyCreateRequest.class))) @Valid ApiKeyCreateRequest request,
            @Context UriInfo uriInfo) {

        String userId = requestContext.get().getUserId();

        log.info("Creating API key '{}' for user '{}' in workspace '{}'",
                request.name(), userId, request.workspaceId());

        Integer expiryDays = null;
        if (request.expiresAt() != null) {
            expiryDays = (int) ChronoUnit.DAYS.between(java.time.Instant.now(), request.expiresAt());
        }

        var result = apiKeyService.generateApiKey(
                userId,
                request.workspaceId(),
                request.name(),
                request.description(),
                Set.of(),
                expiryDays);

        ApiKeyResponse response = ApiKeyResponse.builder()
                .id(result.apiKey().id())
                .name(result.apiKey().name())
                .apiKey(result.plainApiKey())
                .workspaceId(result.apiKey().workspaceId())
                .description(result.apiKey().description())
                .status(result.apiKey().status())
                .expiresAt(result.apiKey().expiresAt())
                .createdAt(result.apiKey().createdAt())
                .lastUsedAt(result.apiKey().lastUsedAt())
                .build();

        log.info("API key '{}' created successfully", request.name());

        return Response.created(uriInfo.getAbsolutePathBuilder().path(result.apiKey().id()).build())
                .entity(response)
                .build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(operationId = "revokeApiKey", summary = "Revoke API key", description = "Revoke/delete an API key", responses = {
            @ApiResponse(responseCode = "204", description = "API Key revoked"),
            @ApiResponse(responseCode = "404", description = "API Key not found", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response revokeApiKey(@PathParam("id") String id) {
        String userId = requestContext.get().getUserId();

        log.info("Revoking API key '{}' by user '{}'", id, userId);

        apiKeyService.revokeApiKey(id, userId);

        log.info("API key '{}' revoked successfully", id);

        return Response.noContent().build();
    }
}
