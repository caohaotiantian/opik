package com.comet.opik.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "User login response")
public record LoginResponse(
        @Schema(description = "User ID") String userId,

        @Schema(description = "Username") String username,

        @Schema(description = "User email") String email,

        @Schema(description = "Full name") String fullName,

        @Schema(description = "Default workspace ID") String defaultWorkspaceId,

        @Schema(description = "Default workspace name") String defaultWorkspaceName,

        @Schema(description = "Whether user is system admin") boolean systemAdmin) {
}
