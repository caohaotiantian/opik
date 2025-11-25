package com.comet.opik.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "User profile update request")
public record UserProfileUpdateRequest(
        @Email(message = "Invalid email format") @Schema(description = "User email address", example = "john@example.com") String email,

        @Schema(description = "Full name", example = "John Doe") String fullName,

        @Schema(description = "Avatar URL") String avatarUrl,

        @Schema(description = "Preferred locale", example = "en-US") String locale) {
}
