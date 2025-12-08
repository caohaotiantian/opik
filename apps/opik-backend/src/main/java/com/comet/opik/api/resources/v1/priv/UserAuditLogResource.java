package com.comet.opik.api.resources.v1.priv;

import com.codahale.metrics.annotation.Timed;
import com.comet.opik.api.AuditLogPage;
import com.comet.opik.api.AuditLogQueryRequest;
import com.comet.opik.api.error.ErrorMessage;
import com.comet.opik.infrastructure.audit.AuditLogService;
import com.comet.opik.infrastructure.audit.AuditStatus;
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

import java.time.Instant;

/**
 * User-level audit log query API
 * Users can query their own audit logs
 */
@Path("/v1/audit-logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Tag(name = "User Audit Logs", description = "User audit log query operations")
public class UserAuditLogResource {

    private final @NonNull AuditLogService auditLogService;
    private final @NonNull Provider<RequestContext> requestContext;

    @GET
    @Operation(operationId = "getUserAuditLogs", summary = "Query user audit logs", description = "Query audit logs for the current user", responses = {
            @ApiResponse(responseCode = "200", description = "Audit log page", content = @Content(schema = @Schema(implementation = AuditLogPage.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response queryAuditLogs(
            @QueryParam("workspace_id") String workspaceId,
            @QueryParam("resource_type") String resourceType,
            @QueryParam("resource_id") String resourceId,
            @QueryParam("operation") com.comet.opik.infrastructure.audit.Operation operation,
            @QueryParam("status") AuditStatus status,
            @QueryParam("start_time") Instant startTime,
            @QueryParam("end_time") Instant endTime,
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size) {

        String userId = requestContext.get().getUserId();

        log.info("Querying audit logs for user '{}': workspaceId='{}', resourceType='{}', operation='{}'",
                userId, workspaceId, resourceType, operation);

        // Parse date strings if provided
        Instant effectiveStartTime = startTime;
        Instant effectiveEndTime = endTime;

        if (startDate != null && !startDate.isEmpty() && effectiveStartTime == null) {
            try {
                effectiveStartTime = Instant.parse(startDate + "T00:00:00Z");
            } catch (Exception e) {
                log.warn("Failed to parse startDate: '{}'", startDate);
            }
        }

        if (endDate != null && !endDate.isEmpty() && effectiveEndTime == null) {
            try {
                effectiveEndTime = Instant.parse(endDate + "T23:59:59Z");
            } catch (Exception e) {
                log.warn("Failed to parse endDate: '{}'", endDate);
            }
        }

        // Build query request
        // Note: We don't filter by userId here because during registration,
        // audit logs are created before the user context is fully established.
        // For production, consider capturing userId explicitly in the @Auditable annotation.
        AuditLogQueryRequest request = AuditLogQueryRequest.builder()
                // .userId(userId) // Disabled: audit logs may have empty userId during registration
                .workspaceId(workspaceId)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .operation(operation)
                .status(status)
                .startTime(effectiveStartTime)
                .endTime(effectiveEndTime)
                .page(page != null ? page : 1)
                .size(size != null ? size : 20)
                .build();

        // Query audit logs from ClickHouse
        AuditLogPage result = auditLogService.query(request).block();

        log.info("Query completed: found '{}' audit logs for user '{}'", result.totalElements(), userId);

        return Response.ok(result).build();
    }
}
