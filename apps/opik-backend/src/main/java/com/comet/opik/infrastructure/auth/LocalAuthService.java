package com.comet.opik.infrastructure.auth;

import com.comet.opik.api.UserStatus;
import com.comet.opik.api.Visibility;
import com.comet.opik.api.WorkspaceStatus;
import com.comet.opik.domain.ApiKeyService;
import com.comet.opik.domain.ProjectService;
import com.comet.opik.domain.SessionService;
import com.comet.opik.domain.UserService;
import com.comet.opik.domain.WorkspaceMemberService;
import com.comet.opik.domain.WorkspaceService;
import jakarta.inject.Provider;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.comet.opik.api.ReactServiceErrorResponse.MISSING_API_KEY;
import static com.comet.opik.api.ReactServiceErrorResponse.MISSING_WORKSPACE;
import static com.comet.opik.api.ReactServiceErrorResponse.NOT_ALLOWED_TO_ACCESS_WORKSPACE;
import static com.comet.opik.infrastructure.auth.RequestContext.WORKSPACE_HEADER;
import static com.comet.opik.infrastructure.auth.RequestContext.WORKSPACE_QUERY_PARAM;

/**
 * Local authentication service that authenticates users using database-stored credentials
 * instead of external React service.
 *
 * Supports:
 * - Session Token authentication (with fingerprint validation)
 * - API Key authentication (with scope validation)
 * - Public endpoint access (for visibility=PUBLIC resources)
 */
@Slf4j
@RequiredArgsConstructor
public class LocalAuthService implements AuthService {

    private static final String USER_NOT_FOUND = "User not found";
    private static final String NOT_LOGGED_USER = "Please login first";
    private static final String INVALID_SESSION = "Invalid or expired session";
    private static final String INVALID_API_KEY = "Invalid or expired API key";

    // Public endpoints that allow unauthenticated access for PUBLIC resources
    private static final Map<String, Set<String>> PUBLIC_ENDPOINTS = new HashMap<>() {
        {
            // Private projects related endpoints
            put("^/v1/private/projects/?$", Set.of("GET"));
            put("^/v1/private/projects/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/?$",
                    Set.of("GET"));
            put("^/v1/private/projects/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/metrics/?$",
                    Set.of("POST"));
            put("^/v1/private/spans/?$", Set.of("GET"));
            put("^/v1/private/spans/stats/?$", Set.of("GET"));
            put("^/v1/private/spans/feedback-scores/names/?$", Set.of("GET"));
            put("^/v1/private/spans/search/?$", Set.of("POST"));
            put("^/v1/private/traces/?$", Set.of("GET"));
            put("^/v1/private/traces/stats/?$", Set.of("GET"));
            put("^/v1/private/traces/feedback-scores/names/?$", Set.of("GET"));
            put("^/v1/private/traces/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/?$",
                    Set.of("GET"));
            put("^/v1/private/traces/threads/?$", Set.of("GET"));
            put("^/v1/private/traces/threads/retrieve/?$", Set.of("POST"));
            put("^/v1/private/traces/search/?$", Set.of("POST"));

            // Public datasets related endpoints
            put("^/v1/private/datasets/?$", Set.of("GET"));
            put("^/v1/private/datasets/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/?$",
                    Set.of("GET"));
            put("^/v1/private/datasets/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/items/?$",
                    Set.of("GET"));
            put("^/v1/private/datasets/items/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/?$",
                    Set.of("GET"));
            put("^/v1/private/datasets/retrieve/?$", Set.of("POST"));
            put("^/v1/private/datasets/items/stream/?$", Set.of("POST"));
        }
    };

    private final @NonNull Provider<RequestContext> requestContext;
    private final @NonNull SessionService sessionService;
    private final @NonNull ApiKeyService apiKeyService;
    private final @NonNull UserService userService;
    private final @NonNull WorkspaceService workspaceService;
    private final @NonNull WorkspaceMemberService memberService;

    @Override
    public void authenticate(HttpHeaders headers, Cookie sessionToken, ContextInfoHolder contextInfo) {
        var uriInfo = contextInfo.uriInfo();
        String path = uriInfo.getRequestUri().getPath();

        // Extract workspace name from header or query parameter
        var currentWorkspaceName = Optional.ofNullable(headers.getHeaderString(WORKSPACE_HEADER))
                .orElseGet(() -> uriInfo.getQueryParameters().getFirst(WORKSPACE_QUERY_PARAM));

        if (StringUtils.isBlank(currentWorkspaceName)) {
            log.warn("Workspace name is missing");
            throw new ClientErrorException(MISSING_WORKSPACE, Response.Status.FORBIDDEN);
        }

        // Handle default workspace (backward compatibility)
        if (isDefaultWorkspace(currentWorkspaceName)) {
            log.debug("Using default workspace");
            requestContext.get().setUserName(ProjectService.DEFAULT_USER);
            requestContext.get().setWorkspaceId(ProjectService.DEFAULT_WORKSPACE_ID);
            requestContext.get().setWorkspaceName(ProjectService.DEFAULT_WORKSPACE_NAME);
            requestContext.get().setApiKey("default");
            return;
        }

        try {
            if (sessionToken != null) {
                authenticateUsingSessionToken(sessionToken, currentWorkspaceName, path, headers);
            } else {
                authenticateUsingApiKey(headers, currentWorkspaceName, path);
            }
        } catch (ClientErrorException authException) {
            // Try public access for public endpoints
            if (isNotAuthenticated(authException) && isEndpointPublic(contextInfo)) {
                log.info("Using visibility PUBLIC for endpoint: '{}'", path);
                handlePublicAccess(currentWorkspaceName);
                return;
            }
            throw authException;
        }
    }

    @Override
    public void authenticateSession(Cookie sessionToken) {
        if (sessionToken == null || StringUtils.isBlank(sessionToken.getValue())) {
            log.info("No session cookie found");
            throw new ClientErrorException(NOT_LOGGED_USER, Response.Status.FORBIDDEN);
        }

        // Just verify session exists, details will be loaded in authenticate()
        log.debug("Session cookie present, will be validated in authenticate()");
    }

    /**
     * Authenticate using session token
     */
    private void authenticateUsingSessionToken(Cookie sessionToken, String workspaceName, String path,
            HttpHeaders headers) {
        log.debug("Authenticating using session token for workspace: '{}'", workspaceName);

        // Validate session with fingerprint
        String ipAddress = extractIpAddress(headers);
        String userAgent = extractUserAgent(headers);

        var session = sessionService.validateSession(sessionToken.getValue(), ipAddress, userAgent);

        if (session.isEmpty()) {
            log.warn("Invalid or expired session");
            throw new ClientErrorException(INVALID_SESSION, Response.Status.UNAUTHORIZED);
        }

        // Load user
        var user = userService.getUser(session.get().userId())
                .orElseThrow(() -> {
                    log.error("User not found for session: '{}'", session.get().userId());
                    return new ClientErrorException(USER_NOT_FOUND, Response.Status.UNAUTHORIZED);
                });

        // Check user status
        if (user.status() != UserStatus.ACTIVE) {
            log.warn("User account is not active: '{}' - status: '{}'", user.id(), user.status());
            throw new ClientErrorException("User account is suspended or deleted", Response.Status.FORBIDDEN);
        }

        // Load workspace
        var workspace = workspaceService.getWorkspaceByName(workspaceName)
                .orElseThrow(() -> {
                    log.warn("Workspace not found: '{}'", workspaceName);
                    return new ClientErrorException("Workspace not found", Response.Status.NOT_FOUND);
                });

        // Check workspace status
        if (workspace.status() != WorkspaceStatus.ACTIVE) {
            log.warn("Workspace is not active: '{}' - status: '{}'", workspace.id(), workspace.status());
            throw new ClientErrorException("Workspace is suspended or deleted", Response.Status.FORBIDDEN);
        }

        // Check workspace membership
        var member = memberService.getMember(workspace.id(), user.id());
        if (member.isEmpty()) {
            log.warn("User '{}' is not a member of workspace '{}'", user.id(), workspace.id());
            throw new ClientErrorException(NOT_ALLOWED_TO_ACCESS_WORKSPACE, Response.Status.FORBIDDEN);
        }

        // Load permissions (TODO: implement permission loading)
        var permissions = loadUserPermissions(user.id(), workspace.id());

        // Set credentials in context
        setCredentialsInContext(user.username(), user.id(), workspace.id(), workspaceName,
                user.systemAdmin(), permissions);
        requestContext.get().setApiKey(sessionToken.getValue());

        // Update last activity async
        sessionService.updateLastActivityAsync(session.get().id());

        log.debug("Session authentication successful for user: '{}'", user.username());
    }

    /**
     * Authenticate using API key
     */
    private void authenticateUsingApiKey(HttpHeaders headers, String workspaceName, String path) {
        log.debug("Authenticating using API key for workspace: '{}'", workspaceName);

        var apiKey = Optional.ofNullable(headers.getHeaderString(HttpHeaders.AUTHORIZATION)).orElse("");
        if (apiKey.isBlank()) {
            log.warn("API key not found in headers");
            throw new ClientErrorException(MISSING_API_KEY, Response.Status.UNAUTHORIZED);
        }

        // Validate API key
        var apiKeyInfo = apiKeyService.validateApiKey(apiKey);
        if (apiKeyInfo.isEmpty()) {
            log.warn("Invalid or expired API key");
            throw new ClientErrorException(INVALID_API_KEY, Response.Status.UNAUTHORIZED);
        }

        // Load user
        var user = userService.getUser(apiKeyInfo.get().userId())
                .orElseThrow(() -> {
                    log.error("User not found for API key: '{}'", apiKeyInfo.get().userId());
                    return new ClientErrorException(USER_NOT_FOUND, Response.Status.UNAUTHORIZED);
                });

        // Check user status
        if (user.status() != UserStatus.ACTIVE) {
            log.warn("User account is not active: '{}' - status: '{}'", user.id(), user.status());
            throw new ClientErrorException("User account is suspended or deleted", Response.Status.FORBIDDEN);
        }

        // Load workspace
        var workspace = workspaceService.getWorkspaceByName(workspaceName)
                .orElseThrow(() -> {
                    log.warn("Workspace not found: '{}'", workspaceName);
                    return new ClientErrorException("Workspace not found", Response.Status.NOT_FOUND);
                });

        // Check workspace status
        if (workspace.status() != WorkspaceStatus.ACTIVE) {
            log.warn("Workspace is not active: '{}' - status: '{}'", workspace.id(), workspace.status());
            throw new ClientErrorException("Workspace is suspended or deleted", Response.Status.FORBIDDEN);
        }

        // Verify API key is for this workspace
        if (!apiKeyInfo.get().workspaceId().equals(workspace.id())) {
            log.warn("API key workspace mismatch: expected '{}', got '{}'", workspace.id(),
                    apiKeyInfo.get().workspaceId());
            throw new ClientErrorException(NOT_ALLOWED_TO_ACCESS_WORKSPACE, Response.Status.FORBIDDEN);
        }

        // Load permissions (limited by API key scopes)
        // Convert scopes to permissions (will be fully implemented in RBAC phase)
        var permissions = new HashSet<String>();

        // Set credentials in context
        setCredentialsInContext(user.username(), user.id(), workspace.id(), workspaceName,
                user.systemAdmin(), permissions);
        requestContext.get().setApiKey(apiKey);

        // Update last used async
        apiKeyService.updateLastUsedAsync(apiKeyInfo.get().id());

        log.debug("API key authentication successful for user: '{}'", user.username());
    }

    /**
     * Handle public access for unauthenticated requests to public endpoints
     */
    private void handlePublicAccess(String workspaceName) {
        var workspace = workspaceService.getWorkspaceByName(workspaceName);

        if (workspace.isEmpty()) {
            log.warn("Workspace not found for public access: '{}'", workspaceName);
            throw new ClientErrorException("Workspace not found", Response.Status.NOT_FOUND);
        }

        if (workspace.get().status() != WorkspaceStatus.ACTIVE) {
            log.warn("Workspace is not active: '{}' - status: '{}'", workspace.get().id(),
                    workspace.get().status());
            throw new ClientErrorException("Workspace is not available", Response.Status.FORBIDDEN);
        }

        requestContext.get().setWorkspaceId(workspace.get().id());
        requestContext.get().setWorkspaceName(workspaceName);
        requestContext.get().setVisibility(Visibility.PUBLIC);
        requestContext.get().setUserName("Public");
    }

    /**
     * Set credentials in request context
     */
    private void setCredentialsInContext(String userName, String userId, String workspaceId, String workspaceName,
            boolean isSystemAdmin, Set<String> permissions) {
        log.debug("Setting credentials in context for user: '{}'", userName);

        requestContext.get().setUserName(userName);
        requestContext.get().setUserId(userId);
        requestContext.get().setWorkspaceId(workspaceId);
        requestContext.get().setWorkspaceName(workspaceName);
        requestContext.get().setSystemAdmin(isSystemAdmin);
        requestContext.get().setPermissions(permissions);

        // Set default quotas (TODO: load from database)
        requestContext.get().setQuotas(java.util.List.of());
    }

    /**
     * Load user permissions from database
     * TODO: Implement actual permission loading logic
     */
    private Set<String> loadUserPermissions(String userId, String workspaceId) {
        // TODO: Implement permission loading from roles
        // For now, return empty set - will be implemented in RBAC phase
        return new HashSet<>();
    }

    /**
     * Extract IP address from headers
     */
    private String extractIpAddress(HttpHeaders headers) {
        // Try X-Forwarded-For first (for proxied requests)
        String ip = headers.getHeaderString("X-Forwarded-For");
        if (StringUtils.isNotBlank(ip)) {
            // Take the first IP if multiple
            return ip.split(",")[0].trim();
        }

        // Fall back to X-Real-IP
        ip = headers.getHeaderString("X-Real-IP");
        if (StringUtils.isNotBlank(ip)) {
            return ip;
        }

        // Default
        return "unknown";
    }

    /**
     * Extract user agent from headers
     */
    private String extractUserAgent(HttpHeaders headers) {
        String userAgent = headers.getHeaderString(HttpHeaders.USER_AGENT);
        return StringUtils.isNotBlank(userAgent) ? userAgent : "unknown";
    }

    /**
     * Check if endpoint is public
     */
    private boolean isEndpointPublic(ContextInfoHolder contextInfo) {
        for (String pattern : PUBLIC_ENDPOINTS.keySet()) {
            if (contextInfo.uriInfo().getRequestUri().getPath().matches(pattern)) {
                Set<String> allowedMethods = PUBLIC_ENDPOINTS.get(pattern);
                if (allowedMethods.contains(contextInfo.method())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if authentication exception is 401 or 403
     */
    private boolean isNotAuthenticated(ClientErrorException authException) {
        int status = authException.getResponse().getStatus();
        return status == Response.Status.UNAUTHORIZED.getStatusCode()
                || status == Response.Status.FORBIDDEN.getStatusCode();
    }

    /**
     * Check if workspace is default workspace
     */
    private boolean isDefaultWorkspace(String workspaceName) {
        return ProjectService.DEFAULT_WORKSPACE_NAME.equalsIgnoreCase(workspaceName);
    }
}
