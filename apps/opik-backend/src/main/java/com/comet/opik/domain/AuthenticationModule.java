package com.comet.opik.domain;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;

/**
 * Guice module for authentication and multi-tenant system
 *
 * Binds DAOs and Services for user authentication, sessions, roles, and multi-tenant management.
 */
@Slf4j
public class AuthenticationModule extends AbstractModule {

    @Override
    protected void configure() {
        log.info("Configuring AuthenticationModule");
        // Services are bound automatically through @Singleton annotation
        log.info("AuthenticationModule configured successfully");
    }

    @Provides
    @Singleton
    public UserDAO userDAO(Jdbi jdbi) {
        return jdbi.onDemand(UserDAO.class);
    }

    @Provides
    @Singleton
    public WorkspaceDAO workspaceDAO(Jdbi jdbi) {
        return jdbi.onDemand(WorkspaceDAO.class);
    }

    @Provides
    @Singleton
    public RoleDAO roleDAO(Jdbi jdbi) {
        return jdbi.onDemand(RoleDAO.class);
    }

    @Provides
    @Singleton
    public WorkspaceMemberDAO workspaceMemberDAO(Jdbi jdbi) {
        return jdbi.onDemand(WorkspaceMemberDAO.class);
    }

    @Provides
    @Singleton
    public ProjectMemberDAO projectMemberDAO(Jdbi jdbi) {
        return jdbi.onDemand(ProjectMemberDAO.class);
    }

    @Provides
    @Singleton
    public ApiKeyDAO apiKeyDAO(Jdbi jdbi) {
        return jdbi.onDemand(ApiKeyDAO.class);
    }

    @Provides
    @Singleton
    public SessionDAO sessionDAO(Jdbi jdbi) {
        return jdbi.onDemand(SessionDAO.class);
    }

    // Note: PasswordResetTokenDAO not yet implemented
}
