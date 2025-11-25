package com.comet.opik.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "User login request")
public record LoginRequest(
        @NotBlank(message = "Username or email is required") @Schema(description = "Username or email", example = "john_doe") String usernameOrEmail,

        @NotBlank(message = "Password is required") @Schema(description = "User password") String password) {
}
