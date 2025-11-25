package com.comet.opik.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.time.Instant;
import java.util.Set;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ApiKey(
        @Schema(description = "API Key ID (UUIDv7)", example = "018c5678-4d9a-7890-b123-456789abcdef") String id,

        @Schema(description = "User ID", example = "018c5678-4d9a-7890-b123-456789abcdef") String userId,

        @Schema(description = "Workspace ID", example = "018c5678-4d9a-7890-b123-456789abcdef") String workspaceId,

        @NotBlank(message = "API key name is required") @Schema(description = "API key name", example = "My API Key") String name,

        @Schema(description = "API key description") String description,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Schema(description = "API key hash (write-only)") String keyHash,

        @Schema(description = "API key prefix (for display)", example = "opik_abc123") String keyPrefix,

        @Schema(description = "API key status", example = "ACTIVE") ApiKeyStatus status,

        @Schema(description = "API key permission scopes") Set<String> scopes,

        @Schema(description = "Expires at timestamp") Instant expiresAt,

        @Schema(description = "Last used timestamp") Instant lastUsedAt,

        @Schema(description = "Version for optimistic locking", example = "0") int version,

        @Schema(description = "Creation timestamp") Instant createdAt,

        @Schema(description = "Created by user") String createdBy,

        @Schema(description = "Last update timestamp") Instant lastUpdatedAt,

        @Schema(description = "Last updated by user") String lastUpdatedBy) {
}
