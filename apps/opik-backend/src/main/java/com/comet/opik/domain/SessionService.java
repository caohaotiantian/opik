package com.comet.opik.domain;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SessionService {

    private final @NonNull SessionDAO sessionDAO;
    private final @NonNull RedissonClient redissonClient;
    private final @NonNull com.comet.opik.infrastructure.SessionConfig sessionConfig;

    private static final String SESSION_CACHE_PREFIX = "session:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Create a new session
     *
     * @param userId the user ID
     * @param ipAddress the IP address
     * @param userAgent the user agent
     * @return the created session
     */
    public com.comet.opik.api.Session createSession(String userId, String ipAddress, String userAgent) {
        log.info("Creating session for user: '{}'", userId);

        // Check and clean old sessions if limit exceeded
        int maxConcurrent = sessionConfig.getMaxConcurrentSessions();
        int activeCount = sessionDAO.countActiveByUser(userId);
        if (activeCount >= maxConcurrent) {
            log.info("User '{}' has '{}' active sessions, cleaning old ones", userId, activeCount);
            sessionDAO.deleteOldUserSessions(userId, maxConcurrent - 1);
        }

        // Generate secure session token
        String sessionToken = generateSecureToken();
        String tokenHash = hashToken(sessionToken);
        String fingerprint = generateFingerprint(ipAddress, userAgent);

        // Use configured session timeout
        long timeoutSeconds = sessionConfig.getSessionTimeoutSeconds();
        Instant expiresAt = Instant.now().plusSeconds(timeoutSeconds);
        Instant now = Instant.now();

        // Create session entity
        var session = com.comet.opik.api.Session.builder()
                .id(java.util.UUID.randomUUID().toString())
                .userId(userId)
                .sessionToken(tokenHash)
                .fingerprint(fingerprint)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .expiresAt(expiresAt)
                .lastActivityAt(now)
                .version(0)
                .createdAt(now)
                .createdBy("system")
                .lastUpdatedAt(now)
                .lastUpdatedBy("system")
                .build();

        // Save to database
        sessionDAO.insert(session);

        // Cache to Redis
        cacheSession(sessionToken, session);

        log.info("Session created successfully for user: '{}', expires at: '{}'", userId, expiresAt);

        // Return session with plain token (only time it's exposed)
        return session.toBuilder().sessionToken(sessionToken).build();
    }

    /**
     * Validate a session token
     *
     * @param sessionToken the session token
     * @param currentIp the current IP address
     * @param currentUserAgent the current user agent
     * @return the session if valid, empty otherwise
     */
    public Optional<com.comet.opik.api.Session> validateSession(String sessionToken, String currentIp,
            String currentUserAgent) {
        log.debug("Validating session token");

        // Try to get from cache first
        var cached = getFromCache(sessionToken);
        if (cached != null) {
            if (cached.expiresAt().isAfter(Instant.now())) {
                // Verify fingerprint
                if (verifyFingerprint(cached, currentIp, currentUserAgent)) {
                    log.debug("Session validated from cache");
                    return Optional.of(cached);
                } else {
                    log.warn("Session fingerprint mismatch for user: '{}'", cached.userId());
                    invalidateSession(sessionToken);
                    return Optional.empty();
                }
            } else {
                log.debug("Cached session expired");
                removeFromCache(sessionToken);
                return Optional.empty();
            }
        }

        // Get from database
        String tokenHash = hashToken(sessionToken);
        var session = sessionDAO.findByTokenHash(tokenHash);

        if (session.isEmpty()) {
            log.debug("Session not found in database");
            return Optional.empty();
        }

        // Check expiration
        if (session.get().expiresAt().isBefore(Instant.now())) {
            log.debug("Session expired");
            sessionDAO.deleteByTokenHash(tokenHash);
            return Optional.empty();
        }

        // Verify fingerprint
        if (!verifyFingerprint(session.get(), currentIp, currentUserAgent)) {
            log.warn("Session fingerprint mismatch for user: '{}'", session.get().userId());
            invalidateSession(sessionToken);
            return Optional.empty();
        }

        // Cache and return
        cacheSession(sessionToken, session.get());
        log.debug("Session validated from database");
        return session;
    }

    /**
     * Update last activity time (async)
     *
     * @param sessionId the session ID
     */
    public void updateLastActivityAsync(String sessionId) {
        CompletableFuture.runAsync(() -> {
            try {
                sessionDAO.updateLastActivity(sessionId, Instant.now());
            } catch (Exception e) {
                log.error("Failed to update session last activity: '{}'", sessionId, e);
            }
        });
    }

    /**
     * Invalidate a session
     *
     * @param sessionToken the session token
     */
    public void invalidateSession(String sessionToken) {
        log.info("Invalidating session");

        String tokenHash = hashToken(sessionToken);
        sessionDAO.deleteByTokenHash(tokenHash);
        removeFromCache(sessionToken);

        log.info("Session invalidated successfully");
    }

    /**
     * Invalidate all sessions for a user
     *
     * @param userId the user ID
     * @return number of sessions deleted
     */
    public int invalidateAllSessions(String userId) {
        log.info("Invalidating all sessions for user: '{}'", userId);

        int deleted = sessionDAO.deleteAllByUser(userId);

        // Note: Cannot easily clear all cached sessions without tracking them
        // They will expire naturally or be invalidated on next use

        log.info("Invalidated '{}' sessions for user: '{}'", deleted, userId);
        return deleted;
    }

    /**
     * Clean up expired sessions
     *
     * @return number of sessions deleted
     */
    public int cleanupExpiredSessions() {
        log.debug("Cleaning up expired sessions");

        int deleted = sessionDAO.deleteExpired(Instant.now());

        if (deleted > 0) {
            log.info("Cleaned up '{}' expired sessions", deleted);
        }

        return deleted;
    }

    /**
     * Generate a secure random token
     */
    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Hash a token using SHA-256
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generate session fingerprint from IP and user agent
     */
    private String generateFingerprint(String ipAddress, String userAgent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = ipAddress + "|" + (userAgent != null ? userAgent : "");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verify session fingerprint
     */
    private boolean verifyFingerprint(com.comet.opik.api.Session session, String currentIp, String currentUserAgent) {
        String currentFingerprint = generateFingerprint(currentIp, currentUserAgent);
        return session.fingerprint().equals(currentFingerprint);
    }

    /**
     * Cache session to Redis
     */
    private void cacheSession(String sessionToken, com.comet.opik.api.Session session) {
        String key = SESSION_CACHE_PREFIX + sessionToken;
        long ttlSeconds = session.expiresAt().getEpochSecond() - Instant.now().getEpochSecond();

        if (ttlSeconds > 0) {
            redissonClient.<com.comet.opik.api.Session>getBucket(key).set(session, ttlSeconds, TimeUnit.SECONDS);
            log.debug("Session cached with TTL: '{}' seconds", ttlSeconds);
        }
    }

    /**
     * Get session from Redis cache
     */
    private com.comet.opik.api.Session getFromCache(String sessionToken) {
        String key = SESSION_CACHE_PREFIX + sessionToken;
        return redissonClient.<com.comet.opik.api.Session>getBucket(key).get();
    }

    /**
     * Remove session from Redis cache
     */
    private void removeFromCache(String sessionToken) {
        String key = SESSION_CACHE_PREFIX + sessionToken;
        redissonClient.getBucket(key).delete();
    }
}
