package com.comet.opik.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Password change request")
public record PasswordChangeRequest(
        @NotBlank(message = "Current password is required") @Schema(description = "Current password") String currentPassword,

        @NotBlank(message = "New password is required") @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters") @Schema(description = "New password (min 8 characters)") String newPassword) {
}
