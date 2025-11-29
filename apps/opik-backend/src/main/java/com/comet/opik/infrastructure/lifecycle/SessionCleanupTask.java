package com.comet.opik.infrastructure.lifecycle;

import com.comet.opik.domain.SessionService;
import io.dropwizard.lifecycle.Managed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Managed lifecycle component for automatic session cleanup
 *
 * Periodically removes expired sessions from the database.
 *
 * Lifecycle:
 * - start(): Starts the scheduled cleanup task
 * - stop(): Shuts down the executor service
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SessionCleanupTask implements Managed {

    private final @NonNull SessionService sessionService;
    private final @NonNull com.comet.opik.infrastructure.SessionConfig sessionConfig;

    private ScheduledExecutorService executorService;

    @Override
    public void start() {
        if (!sessionConfig.isAutoCleanupEnabled()) {
            log.info("Automatic session cleanup is disabled");
            return;
        }

        log.info("Starting automatic session cleanup task");

        executorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "session-cleanup-task");
            thread.setDaemon(true);
            return thread;
        });

        long initialDelay = sessionConfig.getCleanupInitialDelaySeconds();
        long interval = sessionConfig.getCleanupIntervalSeconds();

        executorService.scheduleAtFixedRate(
                this::cleanupExpiredSessions,
                initialDelay,
                interval,
                TimeUnit.SECONDS);

        log.info("Session cleanup task scheduled: initial delay='{}' seconds, interval='{}' seconds",
                initialDelay, interval);
    }

    @Override
    public void stop() {
        if (executorService != null) {
            log.info("Stopping session cleanup task");

            executorService.shutdown();

            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("Session cleanup task did not terminate gracefully, forcing shutdown");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for session cleanup task to terminate", e);
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }

            log.info("Session cleanup task stopped");
        }
    }

    /**
     * Run session cleanup
     */
    private void cleanupExpiredSessions() {
        try {
            log.debug("Running session cleanup task");

            int deleted = sessionService.cleanupExpiredSessions();

            if (deleted > 0) {
                log.info("Session cleanup completed: deleted '{}' expired sessions", deleted);
            } else {
                log.debug("Session cleanup completed: no expired sessions found");
            }

        } catch (Exception e) {
            log.error("Error during session cleanup", e);
        }
    }
}
