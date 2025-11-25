package com.comet.opik.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Update workspace member request")
public record WorkspaceMemberUpdateRequest(
        @NotBlank(message = "Role ID is required") @Schema(description = "New role ID", example = "workspace_admin") String roleId) {
}
