package com.comet.opik.infrastructure.auth;

import com.comet.opik.domain.ApiKeyService;
import com.comet.opik.domain.SessionService;
import com.comet.opik.domain.UserService;
import com.comet.opik.domain.WorkspaceMemberService;
import com.comet.opik.domain.WorkspaceQuotaService;
import com.comet.opik.domain.WorkspaceService;
import com.comet.opik.infrastructure.AuthenticationConfig;
import com.comet.opik.infrastructure.OpikConfiguration;
import com.comet.opik.infrastructure.authorization.PermissionService;
import com.google.common.base.Preconditions;
import com.google.inject.Provides;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonReactiveClient;
import ru.vyarus.dropwizard.guice.module.support.DropwizardAwareModule;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import java.util.Objects;

public class AuthModule extends DropwizardAwareModule<OpikConfiguration> {

    @Provides
    @Singleton
    public AuthService authService(
            @Config("authentication") AuthenticationConfig config,
            @NonNull Provider<RequestContext> requestContext,
            @NonNull RedissonReactiveClient redissonClient,
            @NonNull SessionService sessionService,
            @NonNull WorkspaceService workspaceService,
            @NonNull WorkspaceMemberService workspaceMemberService,
            @NonNull ApiKeyService apiKeyService,
            @NonNull UserService userService,
            @NonNull PermissionService permissionService,
            @NonNull WorkspaceQuotaService quotaService) {

        // When authentication is disabled, use LocalAuthService with default workspace mode (backward compatibility)
        if (!config.isEnabled()) {
            return new LocalAuthService(
                    requestContext,
                    sessionService,
                    apiKeyService,
                    userService,
                    workspaceService,
                    workspaceMemberService,
                    permissionService,
                    quotaService,
                    false); // authEnabled = false (backward compatibility mode)
        }

        // Check authentication type (defaults to LOCAL)
        var authType = config.getType();
        if (authType == null) {
            authType = AuthenticationConfig.AuthType.LOCAL;
        }

        // Use LocalAuthService for LOCAL authentication type
        if (authType == AuthenticationConfig.AuthType.LOCAL) {
            return new LocalAuthService(
                    requestContext,
                    sessionService,
                    apiKeyService,
                    userService,
                    workspaceService,
                    workspaceMemberService,
                    permissionService,
                    quotaService,
                    true); // authEnabled = true (require authentication)
        }

        // REMOTE authentication type requires reactService configuration
        Objects.requireNonNull(config.getReactService(),
                "The property authentication.reactService.url is required when authentication type is REMOTE");

        Preconditions.checkArgument(StringUtils.isNotBlank(config.getReactService().url()),
                "The property authentication.reactService.url must not be blank when authentication type is REMOTE");

        var cacheService = config.getApiKeyResolutionCacheTTLInSec() > 0
                ? new AuthCredentialsCacheService(redissonClient, config.getApiKeyResolutionCacheTTLInSec())
                : new NoopCacheService();

        return new RemoteAuthService(client(), config.getReactService(), requestContext, cacheService);
    }

    public Client client() {
        return ClientBuilder.newClient();
    }
}
