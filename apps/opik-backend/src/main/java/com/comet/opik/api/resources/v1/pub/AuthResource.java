package com.comet.opik.api.resources.v1.pub;

import com.codahale.metrics.annotation.Timed;
import com.comet.opik.api.LoginRequest;
import com.comet.opik.api.LoginResponse;
import com.comet.opik.api.RegisterRequest;
import com.comet.opik.api.Session;
import com.comet.opik.api.User;
import com.comet.opik.api.UserStatus;
import com.comet.opik.api.Workspace;
import com.comet.opik.api.error.ErrorMessage;
import com.comet.opik.domain.PasswordService;
import com.comet.opik.domain.SessionService;
import com.comet.opik.domain.UserService;
import com.comet.opik.domain.WorkspaceService;
import com.comet.opik.infrastructure.auth.RequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Path("/v1/public/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Tag(name = "Authentication", description = "User authentication resources")
public class AuthResource {

    private final @NonNull UserService userService;
    private final @NonNull SessionService sessionService;
    private final @NonNull WorkspaceService workspaceService;
    private final @NonNull PasswordService passwordService;
    private final @NonNull Provider<RequestContext> requestContext;

    @POST
    @Path("/register")
    @Operation(operationId = "register", summary = "Register new user", description = "Register a new user account and create default workspace", responses = {
            @ApiResponse(responseCode = "201", description = "User created successfully", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "409", description = "Username or email already exists", content = @Content(schema = @Schema(implementation = io.dropwizard.jersey.errors.ErrorMessage.class)))
    })
    public Response register(
            @RequestBody(content = @Content(schema = @Schema(implementation = RegisterRequest.class))) @Valid RegisterRequest request) {

        log.info("User registration request for username '{}'", request.username());

        User user = userService.registerUser(
                request.username(),
                request.email(),
                request.password(),
                request.fullName());

        Workspace workspace = workspaceService.createWorkspace(
                user.username() + "-workspace",
                user.username() + "'s Workspace",
                "Default workspace",
                user.id());

        Session session = sessionService.createSession(user.id(), "0.0.0.0", "web-client");

        LoginResponse response = LoginResponse.builder()
                .userId(user.id())
                .username(user.username())
                .email(user.email())
                .fullName(user.fullName())
                .defaultWorkspaceId(workspace.id())
                .defaultWorkspaceName(workspace.name())
                .systemAdmin(user.systemAdmin())
                .build();

        NewCookie sessionCookie = createSessionCookie(session.sessionToken());

        log.info("User '{}' registered successfully with workspace '{}'", user.username(), workspace.name());

        return Response.status(Response.Status.CREATED)
                .entity(response)
                .cookie(sessionCookie)
                .build();
    }

    @POST
    @Path("/login")
    @Operation(operationId = "login", summary = "User login", description = "Authenticate user and create session", responses = {
            @ApiResponse(responseCode = "200", description = "Login successful", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorMessage.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response login(
            @RequestBody(content = @Content(schema = @Schema(implementation = LoginRequest.class))) @Valid LoginRequest request) {

        log.info("User login request for '{}'", request.usernameOrEmail());

        User user = userService.getUserByUsername(request.usernameOrEmail())
                .or(() -> userService.getUserByEmail(request.usernameOrEmail()))
                .orElseThrow(() -> {
                    log.warn("Login failed: user '{}' not found", request.usernameOrEmail());
                    return new BadRequestException("Invalid username/email or password");
                });

        if (!passwordService.verifyPassword(request.password(), user.passwordHash())) {
            log.warn("Login failed: invalid password for user '{}'", user.username());
            throw new BadRequestException("Invalid username/email or password");
        }

        if (user.status() != UserStatus.ACTIVE) {
            log.warn("Login failed: user '{}' is not active (status: {})", user.username(), user.status());
            throw new BadRequestException("User account is " + user.status().name().toLowerCase());
        }

        Workspace defaultWorkspace = workspaceService.getUserWorkspaces(user.id()).stream()
                .findFirst()
                .orElseThrow(() -> {
                    log.error("User '{}' has no accessible workspaces", user.username());
                    return new BadRequestException("User has no accessible workspaces");
                });

        Session session = sessionService.createSession(user.id(), "0.0.0.0", "web-client");

        LoginResponse response = LoginResponse.builder()
                .userId(user.id())
                .username(user.username())
                .email(user.email())
                .fullName(user.fullName())
                .defaultWorkspaceId(defaultWorkspace.id())
                .defaultWorkspaceName(defaultWorkspace.name())
                .systemAdmin(user.systemAdmin())
                .build();

        NewCookie sessionCookie = createSessionCookie(session.sessionToken());

        log.info("User '{}' logged in successfully", user.username());

        return Response.ok()
                .entity(response)
                .cookie(sessionCookie)
                .build();
    }

    @POST
    @Path("/logout")
    @Operation(operationId = "logout", summary = "User logout", description = "Invalidate user session", responses = {
            @ApiResponse(responseCode = "204", description = "Logout successful"),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response logout(@CookieParam(RequestContext.SESSION_COOKIE) Cookie sessionCookie) {

        if (sessionCookie != null && sessionCookie.getValue() != null) {
            String sessionToken = sessionCookie.getValue();
            log.info("User logout request for session '{}'", sessionToken);

            sessionService.invalidateSession(sessionToken);

            log.info("Session '{}' invalidated successfully", sessionToken);
        }

        NewCookie clearCookie = new NewCookie.Builder(RequestContext.SESSION_COOKIE)
                .value("")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(true)
                .build();

        return Response.noContent()
                .cookie(clearCookie)
                .build();
    }

    @POST
    @Path("/logout-all")
    @Operation(operationId = "logoutAll", summary = "Logout all sessions", description = "Invalidate all user sessions", responses = {
            @ApiResponse(responseCode = "204", description = "All sessions invalidated"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response logoutAll(@CookieParam(RequestContext.SESSION_COOKIE) Cookie sessionCookie) {

        if (sessionCookie == null || sessionCookie.getValue() == null) {
            log.warn("Logout all failed: no session cookie");
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new io.dropwizard.jersey.errors.ErrorMessage(401, "Session required"))
                    .build();
        }

        String sessionToken = sessionCookie.getValue();

        // Validate session to get userId
        var sessionOpt = sessionService.validateSession(sessionToken, "0.0.0.0", "");
        if (sessionOpt.isEmpty()) {
            log.warn("Logout all failed: invalid session");
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new io.dropwizard.jersey.errors.ErrorMessage(401, "Invalid session"))
                    .build();
        }

        var session = sessionOpt.get();

        String userId = session.userId();
        log.info("Logout all sessions request for user '{}'", userId);

        int deleted = sessionService.invalidateAllSessions(userId);

        log.info("Invalidated '{}' sessions for user '{}'", deleted, userId);

        NewCookie clearCookie = new NewCookie.Builder(RequestContext.SESSION_COOKIE)
                .value("")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(true)
                .build();

        return Response.noContent()
                .cookie(clearCookie)
                .build();
    }

    private NewCookie createSessionCookie(String sessionToken) {
        return new NewCookie.Builder(RequestContext.SESSION_COOKIE)
                .value(sessionToken)
                .path("/")
                .maxAge(24 * 3600)
                .httpOnly(true)
                .secure(true)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
    }
}
