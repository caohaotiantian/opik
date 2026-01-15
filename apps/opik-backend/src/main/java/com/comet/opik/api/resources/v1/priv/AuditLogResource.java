package com.comet.opik.api.resources.v1.priv;

import com.codahale.metrics.annotation.Timed;
import com.comet.opik.api.AuditLog;
import com.comet.opik.api.AuditLogPage;
import com.comet.opik.api.AuditLogQueryRequest;
import com.comet.opik.api.error.ErrorMessage;
import com.comet.opik.infrastructure.audit.AuditLogService;
import com.comet.opik.infrastructure.audit.AuditStatus;
import com.comet.opik.infrastructure.authorization.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

/**
 * 审计日志查询API
 * 仅系统管理员可访问
 */
@Path("/v1/private/admin/audit-logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Tag(name = "Audit Logs", description = "Audit log query and export operations (System Admin only)")
public class AuditLogResource {

    private final @NonNull AuditLogService auditLogService;

    @GET
    @RequiresPermission("SYSTEM_AUDIT_VIEW")
    @Operation(operationId = "queryAuditLogs", summary = "Query audit logs", description = "Query audit logs with filters and pagination (System Admin only)", responses = {
            @ApiResponse(responseCode = "200", description = "Audit log page", content = @Content(schema = @Schema(implementation = AuditLogPage.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response queryAuditLogs(
            @QueryParam("workspace_id") String workspaceId,
            @QueryParam("user_id") String userId,
            @QueryParam("resource_type") String resourceType,
            @QueryParam("resource_id") String resourceId,
            @QueryParam("operation") com.comet.opik.infrastructure.audit.Operation operation,
            @QueryParam("status") AuditStatus status,
            @QueryParam("start_time") Instant startTime,
            @QueryParam("end_time") Instant endTime,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size,
            @QueryParam("sort_by") String sortBy,
            @QueryParam("sort_direction") String sortDirection) {

        log.info(
                "Querying audit logs with filters: workspaceId='{}', userId='{}', resourceType='{}', operation='{}', status='{}'",
                workspaceId, userId, resourceType, operation, status);

        // 构建查询请求
        AuditLogQueryRequest request = AuditLogQueryRequest.builder()
                .workspaceId(workspaceId)
                .userId(userId)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .operation(operation)
                .status(status)
                .startTime(startTime)
                .endTime(endTime)
                .page(page != null ? page : 0)
                .size(size != null ? size : 20)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .build();

        // Query audit logs from ClickHouse
        AuditLogPage result = auditLogService.query(request).block();

        log.info("Query completed: found '{}' audit logs", result.totalElements());

        return Response.ok(result).build();
    }

    @GET
    @Path("/{id}")
    @RequiresPermission("SYSTEM_AUDIT_VIEW")
    @Operation(operationId = "getAuditLog", summary = "Get audit log by ID", description = "Get detailed information of a specific audit log (System Admin only)", responses = {
            @ApiResponse(responseCode = "200", description = "Audit log details", content = @Content(schema = @Schema(implementation = AuditLog.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "Audit log not found", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response getAuditLog(@PathParam("id") String id) {
        log.info("Getting audit log: id='{}'", id);

        // Query single audit log by ID
        AuditLogQueryRequest request = AuditLogQueryRequest.builder()
                .resourceId(id)
                .page(0)
                .size(1)
                .build();

        AuditLogPage result = auditLogService.query(request).block();

        if (result.content().isEmpty()) {
            log.warn("Audit log not found: id='{}'", id);
            throw new jakarta.ws.rs.NotFoundException("Audit log not found: '%s'".formatted(id));
        }

        log.info("Found audit log: id='{}'", id);
        return Response.ok(result.content().get(0)).build();
    }

    @GET
    @Path("/stats")
    @RequiresPermission("SYSTEM_AUDIT_VIEW")
    @Operation(operationId = "getAuditLogStats", summary = "Get audit log statistics", description = "Get statistics of audit logs (System Admin only)", responses = {
            @ApiResponse(responseCode = "200", description = "Audit log statistics"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response getStats(
            @QueryParam("workspace_id") String workspaceId,
            @QueryParam("start_time") Instant startTime,
            @QueryParam("end_time") Instant endTime) {

        log.info("Getting audit log statistics: workspaceId='{}', startTime='{}', endTime='{}'",
                workspaceId, startTime, endTime);

        // Query logs for statistics
        AuditLogQueryRequest allRequest = AuditLogQueryRequest.builder()
                .workspaceId(workspaceId)
                .startTime(startTime)
                .endTime(endTime)
                .page(0)
                .size(10000) // Get many logs for statistics
                .build();

        AuditLogPage result = auditLogService.query(allRequest).block();

        long total = result.totalElements();
        long success = result.content().stream()
                .filter(log -> log.status() == AuditStatus.SUCCESS)
                .count();
        long failure = result.content().stream()
                .filter(log -> log.status() == AuditStatus.FAILURE)
                .count();

        log.info("Statistics calculated: total='{}', success='{}', failure='{}'", total, success, failure);

        return Response.ok().entity(java.util.Map.of(
                "total", total,
                "success", success,
                "failure", failure)).build();
    }

    @GET
    @Path("/export")
    @RequiresPermission("SYSTEM_AUDIT_VIEW")
    @Produces({MediaType.APPLICATION_JSON, "text/csv"})
    @Operation(operationId = "exportAuditLogs", summary = "Export audit logs", description = "Export audit logs to CSV or JSON format (System Admin only)", responses = {
            @ApiResponse(responseCode = "200", description = "Exported audit logs"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response exportAuditLogs(
            @QueryParam("workspace_id") String workspaceId,
            @QueryParam("user_id") String userId,
            @QueryParam("resource_type") String resourceType,
            @QueryParam("operation") com.comet.opik.infrastructure.audit.Operation operation,
            @QueryParam("status") AuditStatus status,
            @QueryParam("start_time") Instant startTime,
            @QueryParam("end_time") Instant endTime,
            @QueryParam("format") @Schema(description = "Export format: csv or json", defaultValue = "json") String format) {

        log.info("Exporting audit logs: format='{}', workspaceId='{}', startTime='{}', endTime='{}'",
                format, workspaceId, startTime, endTime);

        // Build query request with large size for export
        AuditLogQueryRequest request = AuditLogQueryRequest.builder()
                .workspaceId(workspaceId)
                .userId(userId)
                .resourceType(resourceType)
                .operation(operation)
                .status(status)
                .startTime(startTime)
                .endTime(endTime)
                .page(1)
                .size(100000) // Large size for export
                .build();

        AuditLogPage result = auditLogService.query(request).block();
        java.util.List<AuditLog> logs = result.content();

        log.info("Exporting '{}' audit logs in '{}' format", logs.size(), format);

        // Determine export format
        boolean isCsv = "csv".equalsIgnoreCase(format);
        String contentType = isCsv ? "text/csv" : MediaType.APPLICATION_JSON;
        String filename = "audit_logs_" + Instant.now().toString().replace(":", "-") +
                (isCsv ? ".csv" : ".json");

        Object exportData;
        if (isCsv) {
            exportData = convertToCsv(logs);
        } else {
            exportData = logs;
        }

        return Response.ok(exportData, contentType)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .build();
    }

    /**
     * Convert audit logs to CSV format
     */
    private String convertToCsv(java.util.List<AuditLog> logs) {
        StringBuilder sb = new StringBuilder();

        // CSV header
        sb.append("id,timestamp,workspace_id,user_id,username,action,resource_type,resource_id,")
                .append("resource_name,operation,status,ip_address,user_agent,error_message,created_at\n");

        // CSV rows
        for (AuditLog auditLog : logs) {
            sb.append(escapeCsv(auditLog.id())).append(",")
                    .append(escapeCsv(auditLog.timestamp() != null ? auditLog.timestamp().toString() : "")).append(",")
                    .append(escapeCsv(auditLog.workspaceId())).append(",")
                    .append(escapeCsv(auditLog.userId())).append(",")
                    .append(escapeCsv(auditLog.username())).append(",")
                    .append(escapeCsv(auditLog.action())).append(",")
                    .append(escapeCsv(auditLog.resourceType())).append(",")
                    .append(escapeCsv(auditLog.resourceId())).append(",")
                    .append(escapeCsv(auditLog.resourceName())).append(",")
                    .append(escapeCsv(auditLog.operation() != null ? auditLog.operation().name() : "")).append(",")
                    .append(escapeCsv(auditLog.status() != null ? auditLog.status().name() : "")).append(",")
                    .append(escapeCsv(auditLog.ipAddress())).append(",")
                    .append(escapeCsv(auditLog.userAgent())).append(",")
                    .append(escapeCsv(auditLog.errorMessage())).append(",")
                    .append(escapeCsv(auditLog.createdAt() != null ? auditLog.createdAt().toString() : ""))
                    .append("\n");
        }

        return sb.toString();
    }

    /**
     * Escape special characters for CSV
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // Escape quotes and wrap in quotes if needed
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
