package com.comet.opik.infrastructure.authorization;

import com.comet.opik.infrastructure.auth.RequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.ForbiddenException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * AOP interceptor for @RequiresScope annotation
 *
 * Automatically checks API Key scopes before method execution.
 *
 * Scope check flow:
 * 1. Check if method has @RequiresScope annotation
 * 2. Check if API Key authentication is used (skip if session auth)
 * 3. Extract required scopes from annotation
 * 4. Check if API Key has any of the required scopes
 * 5. Throw ForbiddenException if scope check fails
 * 6. Proceed with method execution if scope check passes
 *
 * Usage:
 * Install this interceptor in Guice module using bindInterceptor()
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ScopeInterceptor implements MethodInterceptor {

    private final @NonNull Provider<RequestContext> requestContextProvider;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();

        // Check if method has @RequiresScope annotation
        RequiresScope annotation = method.getAnnotation(RequiresScope.class);
        if (annotation == null) {
            // No scope check required
            return invocation.proceed();
        }

        RequestContext context = requestContextProvider.get();

        // Scope check only applies to API Key authentication
        if (!context.isApiKeyAuth()) {
            log.debug("Session authentication used, skipping scope check for method: '{}'", method.getName());
            return invocation.proceed();
        }

        // Extract required scopes
        String[] requiredScopes = annotation.value();
        boolean requireAll = annotation.requireAll();
        String customMessage = annotation.message();

        log.debug("Scope check triggered for method: '{}', API Key: '{}', scopes: {}, requireAll: {}",
                method.getName(), context.getApiKey(), Arrays.toString(requiredScopes), requireAll);

        // Check scopes
        boolean hasScope = checkScopes(context, requiredScopes, requireAll);

        if (!hasScope) {
            // Scope check failed
            String errorMessage;
            if (StringUtils.isNotBlank(customMessage)) {
                errorMessage = customMessage;
            } else {
                errorMessage = buildDefaultErrorMessage(method, requiredScopes, requireAll);
            }

            log.warn("Scope check failed for API Key: '{}', method: '{}', required: {}, requireAll: {}",
                    context.getApiKey(), method.getName(), Arrays.toString(requiredScopes), requireAll);

            throw new ForbiddenException(errorMessage);
        }

        // Scope check passed
        log.debug("Scope check passed for API Key: '{}', method: '{}'", context.getApiKey(), method.getName());

        return invocation.proceed();
    }

    /**
     * Check if API Key has required scopes
     */
    private boolean checkScopes(RequestContext context, String[] requiredScopes, boolean requireAll) {
        if (requiredScopes == null || requiredScopes.length == 0) {
            // No scopes required
            return true;
        }

        if (requireAll) {
            // Must have all scopes (AND logic)
            return Arrays.stream(requiredScopes)
                    .allMatch(context::hasApiKeyScope);
        } else {
            // Must have at least one scope (OR logic)
            return Arrays.stream(requiredScopes)
                    .anyMatch(context::hasApiKeyScope);
        }
    }

    /**
     * Build default error message for scope denial
     */
    private String buildDefaultErrorMessage(Method method, String[] requiredScopes, boolean requireAll) {
        if (requiredScopes.length == 1) {
            return String.format("Insufficient API key permissions. Required scope: %s",
                    requiredScopes[0]);
        } else if (requireAll) {
            return String.format(
                    "Insufficient API key permissions. Required scopes (all): %s",
                    Arrays.toString(requiredScopes));
        } else {
            return String.format(
                    "Insufficient API key permissions. Required scopes (any): %s",
                    Arrays.toString(requiredScopes));
        }
    }
}
