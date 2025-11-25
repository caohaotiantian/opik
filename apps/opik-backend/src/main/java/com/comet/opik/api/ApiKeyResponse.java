package com.comet.opik.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "API Key response (includes plaintext key only once after creation)")
public record ApiKeyResponse(
        @Schema(description = "API Key ID") String id,

        @Schema(description = "API Key name") String name,

        @Schema(description = "Plaintext API Key (only returned once after creation)") String apiKey,

        @Schema(description = "Workspace ID") String workspaceId,

        @Schema(description = "Description") String description,

        @Schema(description = "Status") ApiKeyStatus status,

        @Schema(description = "Expiry date") Instant expiresAt,

        @Schema(description = "Created at") Instant createdAt,

        @Schema(description = "Last used at") Instant lastUsedAt) {
}
