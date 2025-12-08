package com.comet.opik.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * API Key creation result containing the plain-text API key (only exposed once)
 */
@Builder(toBuilder = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "API Key creation result with plain-text key")
public record ApiKeyResult(
        @Schema(description = "Plain-text API key (only shown once)", example = "opik_abc123xyz...") String plainApiKey,

        @Schema(description = "API Key metadata") ApiKey apiKey) {
}
