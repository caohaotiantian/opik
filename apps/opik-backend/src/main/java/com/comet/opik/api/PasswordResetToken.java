package com.comet.opik.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PasswordResetToken(
        @Schema(description = "Token ID (UUID)") String id,

        @NotBlank(message = "User ID is required") @Schema(description = "User ID") String userId,

        @NotBlank(message = "Token is required") @Schema(description = "Reset token") String token,

        @Schema(description = "Token status", example = "pending") PasswordResetStatus status,

        @Schema(description = "IP address that requested reset") String ipAddress,

        @Schema(description = "Timestamp when token was used") Instant usedAt,

        @Schema(description = "Expiration timestamp") Instant expiresAt,

        @Schema(description = "Creation timestamp") Instant createdAt,

        @Schema(description = "Created by") String createdBy,

        @Schema(description = "Last update timestamp") Instant lastUpdatedAt,

        @Schema(description = "Last updated by") String lastUpdatedBy) {
}
