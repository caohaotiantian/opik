package com.comet.opik.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Add workspace member request")
public record WorkspaceMemberAddRequest(
        @NotBlank(message = "User ID is required") @Schema(description = "User ID to add", example = "018c5678-4d9a-7890-b123-456789abcdef") String userId,

        @NotBlank(message = "Role ID is required") @Schema(description = "Role ID", example = "workspace_admin") String roleId) {
}
