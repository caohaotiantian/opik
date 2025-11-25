package com.comet.opik.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProjectMember(
        @Schema(description = "Project member ID (UUIDv7)", example = "018c5678-4d9a-7890-b123-456789abcdef") String id,

        @Schema(description = "Project ID", example = "018c5678-4d9a-7890-b123-456789abcdef") String projectId,

        @Schema(description = "User ID", example = "018c5678-4d9a-7890-b123-456789abcdef") String userId,

        @Schema(description = "Role ID", example = "018c5678-4d9a-7890-b123-456789abcdef") String roleId,

        @Schema(description = "Member status", example = "ACTIVE") MemberStatus status,

        @Schema(description = "Version for optimistic locking", example = "0") int version,

        @Schema(description = "Creation timestamp") Instant createdAt,

        @Schema(description = "Created by user") String createdBy,

        @Schema(description = "Last update timestamp") Instant lastUpdatedAt,

        @Schema(description = "Last updated by user") String lastUpdatedBy) {
}
