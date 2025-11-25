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
@Schema(description = "Audit log query request")
public record AuditLogQueryRequest(
        @Schema(description = "Workspace ID filter") String workspaceId,

        @Schema(description = "User ID filter") String userId,

        @Schema(description = "Resource type filter", example = "project") String resourceType,

        @Schema(description = "Resource ID filter") String resourceId,

        @Schema(description = "Operation type filter") Operation operation,

        @Schema(description = "Status filter") AuditStatus status,

        @Schema(description = "Start time (inclusive)") Instant startTime,

        @Schema(description = "End time (exclusive)") Instant endTime,

        @Schema(description = "Page number (0-based)", example = "0") Integer page,

        @Schema(description = "Page size", example = "20") Integer size,

        @Schema(description = "Sort field", example = "timestamp") String sortBy,

        @Schema(description = "Sort direction", example = "DESC", allowableValues = {
                "ASC", "DESC"}) String sortDirection){
    public AuditLogQueryRequest {
        // 设置默认值
        if (page == null || page < 0) {
            page = 0;
        }
        if (size == null || size <= 0) {
            size = 20;
        }
        if (size > 100) {
            size = 100; // 最大每页100条
        }
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "timestamp";
        }
        if (sortDirection == null || sortDirection.isBlank()) {
            sortDirection = "DESC";
        }
    }
}
