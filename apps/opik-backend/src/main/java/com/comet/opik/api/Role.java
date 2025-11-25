package com.comet.opik.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.Instant;
import java.util.Set;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Role(
        @Schema(description = "Role ID (UUIDv7)", example = "018c5678-4d9a-7890-b123-456789abcdef") String id,

        @NotBlank(message = "Role name is required") @Size(min = 3, max = 100, message = "Role name must be between 3 and 100 characters") @Schema(description = "Role name", example = "workspace_admin") String name,

        @NotBlank(message = "Display name is required") @Size(max = 255, message = "Display name must be at most 255 characters") @Schema(description = "Role display name", example = "Workspace Admin") String displayName,

        @Schema(description = "Role description") String description,

        @Schema(description = "Role scope", example = "WORKSPACE") RoleScope scope,

        @Schema(description = "Is builtin role", example = "true") boolean builtin,

        @Schema(description = "Role permissions") Set<String> permissions,

        @Schema(description = "Version for optimistic locking", example = "0") int version,

        @Schema(description = "Creation timestamp") Instant createdAt,

        @Schema(description = "Created by user") String createdBy,

        @Schema(description = "Last update timestamp") Instant lastUpdatedAt,

        @Schema(description = "Last updated by user") String lastUpdatedBy) {
}
