package com.comet.opik.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

/**
 * 当前用户响应
 * 包含用户信息、工作空间列表和默认工作空间
 */
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "CurrentUserResponse", description = "Current user information with workspaces")
public record CurrentUserResponse(
        @Schema(description = "User information") User user,

        @Schema(description = "User's workspaces") List<WorkspaceInfo> workspaces,

        @Schema(description = "Default workspace ID") String defaultWorkspaceId) {

    /**
     * 工作空间简要信息
     */
    @Builder(toBuilder = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(name = "WorkspaceInfo", description = "Workspace brief information")
    public record WorkspaceInfo(
            @Schema(description = "Workspace ID") String id,

            @Schema(description = "Workspace name") String name,

            @Schema(description = "Workspace display name") String displayName,

            @Schema(description = "User's role in workspace") String role) {
    }
}
