package com.comet.opik.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Audit log page result")
public record AuditLogPage(
        @Schema(description = "Audit logs in current page") List<AuditLog> content,

        @Schema(description = "Current page number (0-based)") int page,

        @Schema(description = "Page size") int size,

        @Schema(description = "Total number of elements") long totalElements,

        @Schema(description = "Total number of pages") int totalPages,

        @Schema(description = "Whether this is the first page") boolean first,

        @Schema(description = "Whether this is the last page") boolean last) {
}
