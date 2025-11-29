package com.comet.opik.infrastructure.lifecycle;

import com.google.inject.AbstractModule;

/**
 * Guice module for managed lifecycle components
 *
 * Registers managed components that need to be started/stopped with the application lifecycle.
 */
public class LifecycleModule extends AbstractModule {

    @Override
    protected void configure() {
        // Bind SessionCleanupTask as a Managed component
        // It will be automatically registered with the lifecycle by Guicey
        bind(SessionCleanupTask.class).asEagerSingleton();
    }
}
