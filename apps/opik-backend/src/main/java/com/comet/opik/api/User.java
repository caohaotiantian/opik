package com.comet.opik.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record User(
        @Schema(description = "User ID (UUIDv7)", example = "018c5678-4d9a-7890-b123-456789abcdef") String id,

        @NotBlank(message = "Username is required") @Size(min = 3, max = 100, message = "Username must be between 3 and 100 characters") @Schema(description = "Username for login", example = "john_doe") String username,

        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") @Schema(description = "User email address", example = "john.doe@example.com") String email,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String passwordHash,

        @Schema(description = "User full name", example = "John Doe") String fullName,

        @Schema(description = "Avatar URL", example = "https://example.com/avatar.jpg") String avatarUrl,

        @Schema(description = "User status", example = "ACTIVE") UserStatus status,

        @Schema(description = "Is system administrator", example = "false") boolean systemAdmin,

        @Schema(description = "Email verified", example = "true") boolean emailVerified,

        @Schema(description = "Last login timestamp") Instant lastLoginAt,

        @Schema(description = "User locale", example = "en-US") String locale,

        @Schema(description = "Version for optimistic locking", example = "0") int version,

        @Schema(description = "Creation timestamp") Instant createdAt,

        @Schema(description = "Created by user") String createdBy,

        @Schema(description = "Last update timestamp") Instant lastUpdatedAt,

        @Schema(description = "Last updated by user") String lastUpdatedBy) {
}
