package com.comet.opik.infrastructure.auth;

import com.comet.opik.api.ApiKey;
import com.comet.opik.api.ApiKeyStatus;
import com.comet.opik.api.Session;
import com.comet.opik.api.User;
import com.comet.opik.api.UserStatus;
import com.comet.opik.api.Workspace;
import com.comet.opik.api.WorkspaceStatus;
import com.comet.opik.domain.ApiKeyService;
import com.comet.opik.domain.ProjectService;
import com.comet.opik.domain.SessionService;
import com.comet.opik.domain.UserService;
import com.comet.opik.domain.WorkspaceService;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务单元测试
 *
 * 测试范围：
 * - Session Cookie 认证
 * - API Key 认证
 * - 默认工作空间兼容模式
 * - 工作空间解析
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthServiceImpl单元测试")
class AuthServiceImplTest {

    @Mock
    private RequestContext requestContext;

    @Mock
    private SessionService sessionService;

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private UserService userService;

    @Mock
    private HttpHeaders headers;

    @Mock
    private UriInfo uriInfo;

    private AuthServiceImpl authService;
    private ContextInfoHolder infoHolder;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(() -> requestContext, sessionService, workspaceService, apiKeyService,
                userService);

        infoHolder = ContextInfoHolder.builder()
                .uriInfo(uriInfo)
                .method("GET")
                .build();

        when(uriInfo.getPath()).thenReturn("/api/v1/test");
    }

    @Nested
    @DisplayName("默认工作空间认证测试")
    class DefaultWorkspaceAuthTests {

        @Test
        @DisplayName("无Cookie无Header时应使用默认工作空间")
        void shouldUseDefaultWorkspace_whenNoAuthProvided() {
            // Given
            Cookie sessionToken = null;
            when(headers.getHeaderString(RequestContext.WORKSPACE_HEADER))
                    .thenReturn(ProjectService.DEFAULT_WORKSPACE_NAME);

            // When
            authService.authenticate(headers, sessionToken, infoHolder);

            // Then
            verify(requestContext).setUserName(ProjectService.DEFAULT_USER);
            verify(requestContext).setWorkspaceId(ProjectService.DEFAULT_WORKSPACE_ID);
            verify(requestContext).setWorkspaceName(ProjectService.DEFAULT_WORKSPACE_NAME);
        }

        @Test
        @DisplayName("Cookie无效但使用默认工作空间时应使用默认认证")
        void shouldUseDefaultWorkspace_whenInvalidSessionButDefaultWorkspace() {
            // Given
            Cookie sessionToken = new Cookie("sessionToken", "invalid_token");
            when(headers.getHeaderString(RequestContext.WORKSPACE_HEADER))
                    .thenReturn(ProjectService.DEFAULT_WORKSPACE_NAME);
            when(sessionService.validateSession(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // When
            authService.authenticate(headers, sessionToken, infoHolder);

            // Then
            verify(requestContext).setUserName(ProjectService.DEFAULT_USER);
            verify(requestContext).setWorkspaceId(ProjectService.DEFAULT_WORKSPACE_ID);
        }
    }

    @Nested
    @DisplayName("Session Cookie认证测试")
    class SessionAuthTests {

        @Test
        @DisplayName("有效Session应该成功认证")
        void shouldAuthenticate_whenValidSession() {
            // Given
            String userId = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();
            String sessionToken = "valid_session_token";

            Cookie cookie = new Cookie("sessionToken", sessionToken);

            Session session = Session.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .sessionToken(sessionToken)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            Workspace workspace = Workspace.builder()
                    .id(workspaceId)
                    .name("test-workspace")
                    .displayName("Test Workspace")
                    .status(WorkspaceStatus.ACTIVE)
                    .build();

            when(sessionService.validateSession(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.of(session));
            when(headers.getHeaderString(RequestContext.WORKSPACE_HEADER)).thenReturn(workspaceId);
            when(workspaceService.getWorkspace(workspaceId)).thenReturn(Optional.of(workspace));

            // When
            authService.authenticate(headers, cookie, infoHolder);

            // Then
            verify(requestContext).setUserId(userId);
            verify(requestContext).setWorkspaceId(workspaceId);
            verify(requestContext).setWorkspaceName("test-workspace");
        }

        @Test
        @DisplayName("有效Session使用用户默认工作空间")
        void shouldUseDefaultUserWorkspace_whenNoWorkspaceHeader() {
            // Given
            String userId = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();
            String sessionToken = "valid_session_token";

            Cookie cookie = new Cookie("sessionToken", sessionToken);

            Session session = Session.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .sessionToken(sessionToken)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            Workspace workspace = Workspace.builder()
                    .id(workspaceId)
                    .name("user-workspace")
                    .displayName("User Workspace")
                    .status(WorkspaceStatus.ACTIVE)
                    .build();

            // Note: When WORKSPACE_HEADER is empty, WorkspaceUtils.getWorkspaceName
            // returns DEFAULT_WORKSPACE_NAME, not null
            when(sessionService.validateSession(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.of(session));
            when(headers.getHeaderString(RequestContext.WORKSPACE_HEADER)).thenReturn("");
            when(workspaceService.getWorkspace(ProjectService.DEFAULT_WORKSPACE_NAME)).thenReturn(Optional.empty());
            when(workspaceService.getWorkspaceByName(ProjectService.DEFAULT_WORKSPACE_NAME))
                    .thenReturn(Optional.of(workspace));

            // When
            authService.authenticate(headers, cookie, infoHolder);

            // Then
            verify(requestContext).setUserId(userId);
            verify(requestContext).setWorkspaceId(workspaceId);
            verify(requestContext).setWorkspaceName("user-workspace");
        }

        @Test
        @DisplayName("工作空间不存在时应抛出异常")
        void shouldThrowException_whenWorkspaceNotFound() {
            // Given
            String userId = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();
            String sessionToken = "valid_session_token";

            Cookie cookie = new Cookie("sessionToken", sessionToken);

            Session session = Session.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .sessionToken(sessionToken)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            when(sessionService.validateSession(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.of(session));
            when(headers.getHeaderString(RequestContext.WORKSPACE_HEADER)).thenReturn(workspaceId);
            when(workspaceService.getWorkspace(workspaceId)).thenReturn(Optional.empty());
            when(workspaceService.getWorkspaceByName(workspaceId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> authService.authenticate(headers, cookie, infoHolder))
                    .isInstanceOf(ClientErrorException.class)
                    .hasMessageContaining("Workspace not found");
        }
    }

    @Nested
    @DisplayName("API Key认证测试")
    class ApiKeyAuthTests {

        @Test
        @DisplayName("有效API Key应该成功认证")
        void shouldAuthenticate_whenValidApiKey() {
            // Given
            String userId = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();
            String apiKeyToken = "opik_valid_api_key_token";

            ApiKey apiKey = ApiKey.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .workspaceId(workspaceId)
                    .name("Test API Key")
                    .status(ApiKeyStatus.ACTIVE)
                    .build();

            User user = User.builder()
                    .id(userId)
                    .username("testuser")
                    .email("test@example.com")
                    .status(UserStatus.ACTIVE)
                    .build();

            Workspace workspace = Workspace.builder()
                    .id(workspaceId)
                    .name("api-workspace")
                    .displayName("API Workspace")
                    .status(WorkspaceStatus.ACTIVE)
                    .build();

            when(headers.getHeaderString("Authorization")).thenReturn("Bearer " + apiKeyToken);
            when(apiKeyService.validateApiKey(apiKeyToken)).thenReturn(Optional.of(apiKey));
            when(userService.getUser(userId)).thenReturn(Optional.of(user));
            when(workspaceService.getWorkspace(workspaceId)).thenReturn(Optional.of(workspace));

            // When
            authService.authenticate(headers, null, infoHolder);

            // Then
            verify(requestContext).setUserId(userId);
            verify(requestContext).setUserName("testuser");
            verify(requestContext).setApiKey(apiKeyToken);
            verify(requestContext).setWorkspaceId(workspaceId);
            verify(apiKeyService).updateLastUsedAsync(apiKey.id());
        }

        @Test
        @DisplayName("无效API Key应该抛出NotAuthorizedException")
        void shouldThrowNotAuthorized_whenInvalidApiKey() {
            // Given
            String invalidApiKey = "opik_invalid_api_key";

            when(headers.getHeaderString("Authorization")).thenReturn("Bearer " + invalidApiKey);
            when(apiKeyService.validateApiKey(invalidApiKey)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> authService.authenticate(headers, null, infoHolder))
                    .isInstanceOf(NotAuthorizedException.class);
        }

        @Test
        @DisplayName("用户不存在时应该抛出NotAuthorizedException")
        void shouldThrowNotAuthorized_whenUserNotFound() {
            // Given
            String userId = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();
            String apiKeyToken = "opik_valid_api_key";

            ApiKey apiKey = ApiKey.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .workspaceId(workspaceId)
                    .name("Test API Key")
                    .status(ApiKeyStatus.ACTIVE)
                    .build();

            when(headers.getHeaderString("Authorization")).thenReturn("Bearer " + apiKeyToken);
            when(apiKeyService.validateApiKey(apiKeyToken)).thenReturn(Optional.of(apiKey));
            when(userService.getUser(userId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> authService.authenticate(headers, null, infoHolder))
                    .isInstanceOf(NotAuthorizedException.class);
        }

        @Test
        @DisplayName("用户被禁用时应该抛出ForbiddenException")
        void shouldThrowForbidden_whenUserSuspended() {
            // Given
            String userId = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();
            String apiKeyToken = "opik_valid_api_key";

            ApiKey apiKey = ApiKey.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .workspaceId(workspaceId)
                    .name("Test API Key")
                    .status(ApiKeyStatus.ACTIVE)
                    .build();

            User suspendedUser = User.builder()
                    .id(userId)
                    .username("suspended_user")
                    .email("suspended@example.com")
                    .status(UserStatus.SUSPENDED)
                    .build();

            when(headers.getHeaderString("Authorization")).thenReturn("Bearer " + apiKeyToken);
            when(apiKeyService.validateApiKey(apiKeyToken)).thenReturn(Optional.of(apiKey));
            when(userService.getUser(userId)).thenReturn(Optional.of(suspendedUser));

            // When & Then
            assertThatThrownBy(() -> authService.authenticate(headers, null, infoHolder))
                    .isInstanceOf(ClientErrorException.class)
                    .hasMessageContaining("User account is suspended");
        }
    }

    @Nested
    @DisplayName("无认证信息测试")
    class NoAuthTests {

        @Test
        @DisplayName("无认证且非默认工作空间时应抛出NotAuthorizedException")
        void shouldThrowNotAuthorized_whenNoAuthAndNonDefaultWorkspace() {
            // Given
            when(headers.getHeaderString(RequestContext.WORKSPACE_HEADER)).thenReturn("non-default-workspace");
            when(headers.getHeaderString("Authorization")).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> authService.authenticate(headers, null, infoHolder))
                    .isInstanceOf(NotAuthorizedException.class);
        }
    }

    @Nested
    @DisplayName("Session专用认证测试")
    class AuthenticateSessionTests {

        @Test
        @DisplayName("有效Session应该成功认证")
        void shouldAuthenticate_whenValidSession() {
            // Given
            String userId = UUID.randomUUID().toString();
            String sessionToken = "valid_session_token";
            Cookie cookie = new Cookie("sessionToken", sessionToken);

            Session session = Session.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .sessionToken(sessionToken)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            when(sessionService.validateSession(eq(sessionToken), anyString(), anyString()))
                    .thenReturn(Optional.of(session));

            // When
            authService.authenticateSession(cookie);

            // Then
            verify(requestContext).setUserId(userId);
        }

        @Test
        @DisplayName("无效Session应该抛出NotAuthorizedException")
        void shouldThrowNotAuthorized_whenInvalidSession() {
            // Given
            String sessionToken = "invalid_session_token";
            Cookie cookie = new Cookie("sessionToken", sessionToken);

            when(sessionService.validateSession(eq(sessionToken), anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> authService.authenticateSession(cookie))
                    .isInstanceOf(NotAuthorizedException.class);
        }

        @Test
        @DisplayName("无Cookie时应该抛出NotAuthorizedException")
        void shouldThrowNotAuthorized_whenNoCookie() {
            // When & Then
            assertThatThrownBy(() -> authService.authenticateSession(null))
                    .isInstanceOf(NotAuthorizedException.class);
        }
    }
}
