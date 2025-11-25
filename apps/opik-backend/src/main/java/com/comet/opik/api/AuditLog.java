package com.comet.opik.api;

import com.comet.opik.infrastructure.audit.AuditStatus;
import com.comet.opik.infrastructure.audit.Operation;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Audit log entry")
public record AuditLog(
        @Schema(description = "Audit log ID") String id,

        @Schema(description = "Workspace ID") String workspaceId,

        @Schema(description = "User ID who performed the action") String userId,

        @Schema(description = "Username") String username,

        @Schema(description = "Action description", example = "Create Project") String action,

        @Schema(description = "Resource type", example = "project") String resourceType,

        @Schema(description = "Resource ID") String resourceId,

        @Schema(description = "Resource name") String resourceName,

        @Schema(description = "Operation type") Operation operation,

        @Schema(description = "Operation status") AuditStatus status,

        @Schema(description = "Client IP address") String ipAddress,

        @Schema(description = "User agent") String userAgent,

        @Schema(description = "Request path", example = "/v1/private/projects") String requestPath,

        @Schema(description = "Request method", example = "POST") String requestMethod,

        @Schema(description = "Changes in JSON format") String changes,

        @Schema(description = "Error message if operation failed") String errorMessage,

        @Schema(description = "Operation duration in milliseconds") Integer durationMs,

        @Schema(description = "Timestamp of the operation") Instant timestamp,

        @Schema(description = "Created at") Instant createdAt,

        @Schema(description = "Created by") String createdBy) {
}
