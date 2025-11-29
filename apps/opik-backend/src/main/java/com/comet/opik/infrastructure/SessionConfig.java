package com.comet.opik.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.util.Duration;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Configuration for Session management
 *
 * Controls session timeout, concurrent session limits, and cleanup behavior.
 */
@Data
public class SessionConfig {

    /**
     * Session timeout duration
     * Default: 24 hours
     */
    @JsonProperty
    @NotNull private Duration sessionTimeout = Duration.hours(24);

    /**
     * Maximum number of concurrent sessions per user
     * Default: 5
     */
    @JsonProperty
    @NotNull private int maxConcurrentSessions = 5;

    /**
     * Whether to enable automatic session cleanup
     * Default: true
     */
    @JsonProperty
    @NotNull private boolean autoCleanupEnabled = true;

    /**
     * Interval between automatic session cleanup runs
     * Default: 1 hour
     */
    @JsonProperty
    @NotNull private Duration cleanupInterval = Duration.hours(1);

    /**
     * Delay before starting the first cleanup task
     * Default: 5 minutes
     */
    @JsonProperty
    @NotNull private Duration cleanupInitialDelay = Duration.minutes(5);

    /**
     * Get session timeout in seconds
     */
    public long getSessionTimeoutSeconds() {
        return sessionTimeout.toSeconds();
    }

    /**
     * Get cleanup interval in seconds
     */
    public long getCleanupIntervalSeconds() {
        return cleanupInterval.toSeconds();
    }

    /**
     * Get cleanup initial delay in seconds
     */
    public long getCleanupInitialDelaySeconds() {
        return cleanupInitialDelay.toSeconds();
    }
}
