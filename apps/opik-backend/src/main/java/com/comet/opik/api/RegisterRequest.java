package com.comet.opik.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "User registration request")
public record RegisterRequest(
        @NotBlank(message = "Username is required") @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters") @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Username can only contain letters, numbers, underscores and hyphens") @Schema(description = "Unique username", example = "john_doe") String username,

        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") @Schema(description = "User email address", example = "john@example.com") String email,

        @NotBlank(message = "Password is required") @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters") @Schema(description = "User password (min 8 characters)", example = "SecurePass123!") String password,

        @Schema(description = "Full name", example = "John Doe") String fullName,

        @Schema(description = "Preferred locale", example = "en-US", defaultValue = "en-US") String locale) {
}
