package com.comet.opik.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

/**
 * Workspace member response with user details
 */
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WorkspaceMemberResponse(
        @Schema(description = "Workspace member ID", example = "018c5678-4d9a-7890-b123-456789abcdef") String id,

        @Schema(description = "User ID", example = "018c5678-4d9a-7890-b123-456789abcdef") String userId,

        @Schema(description = "Username") String username,

        @Schema(description = "User email") String email,

        @Schema(description = "User full name") String fullName,

        @Schema(description = "User avatar URL") String avatarUrl,

        @Schema(description = "Role ID", example = "018c5678-4d9a-7890-b123-456789abcdef") String roleId,

        @Schema(description = "Role name") String roleName,

        @Schema(description = "Role display name") String roleDisplayName,

        @Schema(description = "Member status", example = "active") String status,

        @Schema(description = "Joined timestamp") Instant joinedAt) {

    /**
     * Create response from member, user, and role
     */
    public static WorkspaceMemberResponse from(WorkspaceMember member, User user, Role role) {
        return WorkspaceMemberResponse.builder()
                .id(member.id())
                .userId(member.userId())
                .username(user != null ? user.username() : "Unknown")
                .email(user != null ? user.email() : "")
                .fullName(user != null ? user.fullName() : null)
                .avatarUrl(user != null ? user.avatarUrl() : null)
                .roleId(member.roleId())
                .roleName(role != null ? role.name() : "")
                .roleDisplayName(role != null ? role.displayName() : "")
                .status(member.status() != null ? member.status().toValue() : "active")
                .joinedAt(member.createdAt())
                .build();
    }
}
