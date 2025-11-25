package com.comet.opik.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Workspace creation request")
public record WorkspaceCreateRequest(
        @NotBlank(message = "Name is required") @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters") @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Name can only contain letters, numbers, underscores and hyphens") @Schema(description = "Workspace unique name", example = "my-workspace") String name,

        @Schema(description = "Display name", example = "My Workspace") String displayName,

        @Schema(description = "Description") String description) {
}
