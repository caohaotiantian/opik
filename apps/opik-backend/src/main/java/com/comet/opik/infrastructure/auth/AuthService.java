package com.comet.opik.infrastructure.auth;

import com.comet.opik.api.ApiKey;
import com.comet.opik.api.UserStatus;
import com.comet.opik.domain.ApiKeyService;
import com.comet.opik.domain.ProjectService;
import com.comet.opik.domain.SessionService;
import com.comet.opik.domain.UserService;
import com.comet.opik.domain.WorkspaceService;
import com.comet.opik.utils.WorkspaceUtils;
import jakarta.inject.Provider;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.comet.opik.infrastructure.auth.RequestContext.WORKSPACE_HEADER;

public interface AuthService {

    void authenticate(HttpHeaders headers, Cookie sessionToken, ContextInfoHolder contextInfo);
    void authenticateSession(Cookie sessionToken);
}

@Slf4j
@RequiredArgsConstructor
class AuthServiceImpl implements AuthService {

    private final @NonNull Provider<RequestContext> requestContext;
    private final @NonNull SessionService sessionService;
    private final @NonNull WorkspaceService workspaceService;
    private final @NonNull ApiKeyService apiKeyService;
    private final @NonNull UserService userService;

    @Override
    public void authenticate(HttpHeaders headers, Cookie sessionToken, ContextInfoHolder contextInfo) {
        log.info("Authenticating request to '{}'", contextInfo.uriInfo().getPath());

        // 1. Try session cookie authentication
        if (sessionToken != null && sessionToken.getValue() != null) {
            String token = sessionToken.getValue();
            log.info("Session token found, validating");

            // Get client info from context
            String ipAddress = getClientIpAddress(contextInfo);
            String userAgent = headers.getHeaderString("User-Agent");
            if (userAgent == null) {
                userAgent = "";
            }

            log.info("Validating session with IP: '{}', UserAgent: '{}'", ipAddress, userAgent);
            var sessionOpt = sessionService.validateSession(token, ipAddress, userAgent);

            if (sessionOpt.isPresent()) {
                var session = sessionOpt.get();
                log.info("Session valid for user: '{}'", session.userId());

                // Set user ID in request context
                requestContext.get().setUserId(session.userId());

                // Handle workspace from header or use default
                var workspaceHeader = WorkspaceUtils.getWorkspaceName(headers.getHeaderString(WORKSPACE_HEADER));
                log.info("Workspace header received: '{}'", workspaceHeader);

                if (workspaceHeader != null && !workspaceHeader.isEmpty()) {
                    // Check if it's the default workspace (backward compatibility for open source)
                    if (ProjectService.DEFAULT_WORKSPACE_NAME.equalsIgnoreCase(workspaceHeader)) {
                        log.info("Using default workspace for backward compatibility");
                        requestContext.get().setWorkspaceId(ProjectService.DEFAULT_WORKSPACE_ID);
                        requestContext.get().setWorkspaceName(ProjectService.DEFAULT_WORKSPACE_NAME);
                        requestContext.get().setUserName(session.userId());
                        return;
                    }

                    // Try to get workspace by ID first, then by name
                    log.info("Looking up workspace by ID: '{}'", workspaceHeader);
                    var workspaceOpt = workspaceService.getWorkspace(workspaceHeader);
                    log.info("Workspace by ID result: '{}'",
                            workspaceOpt.isPresent() ? workspaceOpt.get().name() : "NOT FOUND");
                    if (workspaceOpt.isEmpty()) {
                        log.info("Looking up workspace by name: '{}'", workspaceHeader);
                        workspaceOpt = workspaceService.getWorkspaceByName(workspaceHeader);
                        log.info("Workspace by name result: '{}'",
                                workspaceOpt.isPresent() ? workspaceOpt.get().name() : "NOT FOUND");
                    }
                    if (workspaceOpt.isPresent()) {
                        var workspace = workspaceOpt.get();
                        requestContext.get().setWorkspaceId(workspace.id());
                        requestContext.get().setWorkspaceName(workspace.name());
                        log.info("Workspace set: '{}'", workspace.name());
                    } else {
                        log.warn("Workspace not found: '{}'", workspaceHeader);
                        throw new ClientErrorException("Workspace not found", Response.Status.NOT_FOUND);
                    }
                } else {
                    // Use user's first workspace as default
                    var workspaces = workspaceService.getUserWorkspaces(session.userId());
                    if (!workspaces.isEmpty()) {
                        var defaultWorkspace = workspaces.get(0);
                        requestContext.get().setWorkspaceId(defaultWorkspace.id());
                        requestContext.get().setWorkspaceName(defaultWorkspace.name());
                        log.info("Using default workspace: '{}'", defaultWorkspace.name());
                    }
                }

                return;
            } else {
                log.warn("Invalid session token");
            }
        }

        // 2. Try API key authentication
        String authHeader = headers.getHeaderString("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String apiKeyToken = authHeader.substring(7);
            log.info("API key found, validating...");

            var apiKeyOpt = apiKeyService.validateApiKey(apiKeyToken);
            if (apiKeyOpt.isPresent()) {
                ApiKey apiKey = apiKeyOpt.get();
                log.info("API key validated for user: '{}'", apiKey.userId());

                // Check user status
                var userOpt = userService.getUser(apiKey.userId());
                if (userOpt.isEmpty()) {
                    log.warn("User not found for API key: '{}'", apiKey.userId());
                    throw new NotAuthorizedException("Invalid API key - user not found");
                }

                var user = userOpt.get();
                if (user.status() != UserStatus.ACTIVE) {
                    log.warn("User account is not active: '{}' - status: '{}'", user.id(), user.status());
                    throw new ClientErrorException("User account is suspended", Response.Status.FORBIDDEN);
                }

                // Set user ID in request context
                requestContext.get().setUserId(apiKey.userId());
                requestContext.get().setUserName(user.username());
                requestContext.get().setApiKey(apiKeyToken);

                // Handle workspace - use API key's workspace directly
                // Note: Workspace validation relaxed for development
                requestContext.get().setWorkspaceId(apiKey.workspaceId());
                var workspaceOpt = workspaceService.getWorkspace(apiKey.workspaceId());
                if (workspaceOpt.isPresent()) {
                    requestContext.get().setWorkspaceName(workspaceOpt.get().name());
                    log.info("Using API key workspace: '{}'", workspaceOpt.get().name());
                } else {
                    log.warn("API key workspace not found: '{}'", apiKey.workspaceId());
                }

                // Update last used time asynchronously
                apiKeyService.updateLastUsedAsync(apiKey.id());

                log.info("API key authentication successful for user: '{}'", apiKey.userId());
                return;
            } else {
                log.warn("Invalid API key");
                throw new NotAuthorizedException("Invalid or expired API key");
            }
        }

        // 3. Check for default workspace (backward compatibility)
        var currentWorkspaceName = WorkspaceUtils.getWorkspaceName(headers.getHeaderString(WORKSPACE_HEADER));
        if (ProjectService.DEFAULT_WORKSPACE_NAME.equals(currentWorkspaceName)) {
            log.debug("Using default workspace for local installation");
            requestContext.get().setUserName(ProjectService.DEFAULT_USER);
            requestContext.get().setWorkspaceId(ProjectService.DEFAULT_WORKSPACE_ID);
            requestContext.get().setWorkspaceName(ProjectService.DEFAULT_WORKSPACE_NAME);
            requestContext.get().setApiKey("default");
            return;
        }

        // 4. No valid authentication found
        log.warn("Authentication failed for request to '{}'", contextInfo.uriInfo().getPath());
        throw new NotAuthorizedException("Authentication required");
    }

    @Override
    public void authenticateSession(Cookie sessionToken) {
        log.debug("Authenticating session");

        if (sessionToken != null && sessionToken.getValue() != null) {
            String token = sessionToken.getValue();

            // For session endpoints, we just validate the session exists
            // IP and user agent validation is less strict here
            var sessionOpt = sessionService.validateSession(token, "0.0.0.0", "");

            if (sessionOpt.isPresent()) {
                var session = sessionOpt.get();
                log.debug("Session authenticated for user: '{}'", session.userId());
                requestContext.get().setUserId(session.userId());
                return;
            }
        }

        log.warn("Session authentication failed");
        throw new NotAuthorizedException("Invalid or expired session");
    }

    private String getClientIpAddress(ContextInfoHolder contextInfo) {
        // For now, use consistent IP address
        // In production, this should extract from X-Forwarded-For header
        return "0.0.0.0";
    }
}
