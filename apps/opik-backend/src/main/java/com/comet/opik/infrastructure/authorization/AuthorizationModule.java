package com.comet.opik.infrastructure.authorization;

import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;

/**
 * Guice module for RBAC authorization system
 *
 * Binds permission services and configures AOP interceptor for @RequiresPermission annotation.
 */
public class AuthorizationModule extends AbstractModule {

    @Override
    protected void configure() {
        // Bind permission services
        bind(PermissionService.class);
        bind(PermissionCacheService.class);

        // Configure AOP interceptor for @RequiresPermission annotation
        // Intercepts all methods annotated with @RequiresPermission
        PermissionInterceptor permissionInterceptor = new PermissionInterceptor(
                getProvider(com.comet.opik.infrastructure.auth.RequestContext.class),
                getProvider(PermissionService.class).get());

        bindInterceptor(
                Matchers.any(), // Match all classes
                Matchers.annotatedWith(RequiresPermission.class), // Match methods with @RequiresPermission
                permissionInterceptor);
    }
}
