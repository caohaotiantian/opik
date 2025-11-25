package com.comet.opik.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "API Key creation request")
public record ApiKeyCreateRequest(
        @NotBlank(message = "Name is required") @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters") @Schema(description = "API Key name", example = "Production API Key") String name,

        @NotBlank(message = "Workspace ID is required") @Schema(description = "Workspace ID", example = "018c5678-4d9a-7890-b123-456789abcdef") String workspaceId,

        @Schema(description = "Description") String description,

        @Schema(description = "Expiry date (optional)", example = "2025-12-31T23:59:59Z") Instant expiresAt) {
}
