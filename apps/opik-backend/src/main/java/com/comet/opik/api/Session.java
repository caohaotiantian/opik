package com.comet.opik.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.io.Serializable;
import java.time.Instant;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Session(
        @Schema(description = "Session ID (UUIDv7)", example = "018c5678-4d9a-7890-b123-456789abcdef") String id,

        @Schema(description = "User ID", example = "018c5678-4d9a-7890-b123-456789abcdef") String userId,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Schema(description = "Session token (write-only)") String sessionToken,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Schema(description = "Session fingerprint (write-only)") String fingerprint,

        @Schema(description = "IP address", example = "192.168.1.100") String ipAddress,

        @Schema(description = "User agent") String userAgent,

        @Schema(description = "Expires at timestamp") Instant expiresAt,

        @Schema(description = "Last activity timestamp") Instant lastActivityAt,

        @Schema(description = "Version for optimistic locking", example = "0") int version,

        @Schema(description = "Creation timestamp") Instant createdAt,

        @Schema(description = "Created by user") String createdBy,

        @Schema(description = "Last update timestamp") Instant lastUpdatedAt,

        @Schema(description = "Last updated by user") String lastUpdatedBy) implements Serializable {
}
