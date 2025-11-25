package com.comet.opik.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Workspace(
        @Schema(description = "Workspace ID (UUIDv7)", example = "018c5678-4d9a-7890-b123-456789abcdef") String id,

        @NotBlank(message = "Workspace name is required") @Size(min = 3, max = 150, message = "Workspace name must be between 3 and 150 characters") @Schema(description = "Workspace unique name", example = "my-workspace") String name,

        @NotBlank(message = "Display name is required") @Size(max = 255, message = "Display name must be at most 255 characters") @Schema(description = "Workspace display name", example = "My Workspace") String displayName,

        @Schema(description = "Workspace description") String description,

        @Schema(description = "Workspace status", example = "ACTIVE") WorkspaceStatus status,

        @NotBlank(message = "Owner user ID is required") @Schema(description = "Owner user ID", example = "018c5678-4d9a-7890-b123-456789abcdef") String ownerUserId,

        @Schema(description = "Quota limit", example = "10") Integer quotaLimit,

        @Schema(description = "Allow public access", example = "false") boolean allowPublicAccess,

        @Schema(description = "Maximum members", example = "100") Integer maxMembers,

        @Schema(description = "Additional settings (JSON)") String settings,

        @Schema(description = "Version for optimistic locking", example = "0") int version,

        @Schema(description = "Creation timestamp") Instant createdAt,

        @Schema(description = "Created by user") String createdBy,

        @Schema(description = "Last update timestamp") Instant lastUpdatedAt,

        @Schema(description = "Last updated by user") String lastUpdatedBy) {
}
