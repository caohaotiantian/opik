package com.comet.opik.infrastructure.authorization;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Permission cache service using Redis
 *
 * Caches user permissions to reduce database queries and improve performance.
 * Cache TTL: 10 minutes (600 seconds)
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PermissionCacheService {

    private final @NonNull RedissonClient redissonClient;

    private static final String WORKSPACE_PERMISSION_PREFIX = "permission:workspace:";
    private static final String PROJECT_PERMISSION_PREFIX = "permission:project:";
    private static final int CACHE_TTL_SECONDS = 600; // 10 minutes

    /**
     * Get cached workspace permissions for user
     *
     * @param userId the user ID
     * @param workspaceId the workspace ID
     * @return optional set of permission names, empty if not cached
     */
    public Optional<Set<String>> getWorkspacePermissions(String userId, String workspaceId) {
        String key = buildWorkspaceKey(userId, workspaceId);

        try {
            var bucket = redissonClient.getBucket(key);
            @SuppressWarnings("unchecked")
            Set<String> permissions = (Set<String>) bucket.get();

            if (permissions != null) {
                log.debug("Cache hit for workspace permissions: user='{}', workspace='{}'", userId, workspaceId);
                return Optional.of(permissions);
            }

            log.debug("Cache miss for workspace permissions: user='{}', workspace='{}'", userId, workspaceId);
            return Optional.empty();

        } catch (Exception e) {
            log.error("Failed to get workspace permissions from cache: user='{}', workspace='{}'", userId,
                    workspaceId, e);
            return Optional.empty();
        }
    }

    /**
     * Cache workspace permissions for user
     *
     * @param userId the user ID
     * @param workspaceId the workspace ID
     * @param permissions the set of permission names to cache
     */
    public void cacheWorkspacePermissions(String userId, String workspaceId, Set<String> permissions) {
        String key = buildWorkspaceKey(userId, workspaceId);

        try {
            var bucket = redissonClient.getBucket(key);
            bucket.set(permissions, CACHE_TTL_SECONDS, TimeUnit.SECONDS);

            log.debug("Cached workspace permissions: user='{}', workspace='{}', count='{}'", userId, workspaceId,
                    permissions.size());

        } catch (Exception e) {
            log.error("Failed to cache workspace permissions: user='{}', workspace='{}'", userId, workspaceId, e);
        }
    }

    /**
     * Get cached project permissions for user
     *
     * @param userId the user ID
     * @param projectId the project ID
     * @return optional set of permission names, empty if not cached
     */
    public Optional<Set<String>> getProjectPermissions(String userId, String projectId) {
        String key = buildProjectKey(userId, projectId);

        try {
            var bucket = redissonClient.getBucket(key);
            @SuppressWarnings("unchecked")
            Set<String> permissions = (Set<String>) bucket.get();

            if (permissions != null) {
                log.debug("Cache hit for project permissions: user='{}', project='{}'", userId, projectId);
                return Optional.of(permissions);
            }

            log.debug("Cache miss for project permissions: user='{}', project='{}'", userId, projectId);
            return Optional.empty();

        } catch (Exception e) {
            log.error("Failed to get project permissions from cache: user='{}', project='{}'", userId, projectId, e);
            return Optional.empty();
        }
    }

    /**
     * Cache project permissions for user
     *
     * @param userId the user ID
     * @param projectId the project ID
     * @param permissions the set of permission names to cache
     */
    public void cacheProjectPermissions(String userId, String projectId, Set<String> permissions) {
        String key = buildProjectKey(userId, projectId);

        try {
            var bucket = redissonClient.getBucket(key);
            bucket.set(permissions, CACHE_TTL_SECONDS, TimeUnit.SECONDS);

            log.debug("Cached project permissions: user='{}', project='{}', count='{}'", userId, projectId,
                    permissions.size());

        } catch (Exception e) {
            log.error("Failed to cache project permissions: user='{}', project='{}'", userId, projectId, e);
        }
    }

    /**
     * Invalidate workspace permissions cache for user
     *
     * Call this when:
     * - User's workspace role changes
     * - User is added/removed from workspace
     * - Role permissions are updated
     *
     * @param userId the user ID
     * @param workspaceId the workspace ID
     */
    public void invalidateWorkspacePermissions(String userId, String workspaceId) {
        String key = buildWorkspaceKey(userId, workspaceId);

        try {
            redissonClient.getBucket(key).delete();
            log.info("Invalidated workspace permissions cache: user='{}', workspace='{}'", userId, workspaceId);

        } catch (Exception e) {
            log.error("Failed to invalidate workspace permissions cache: user='{}', workspace='{}'", userId,
                    workspaceId, e);
        }
    }

    /**
     * Invalidate project permissions cache for user
     *
     * Call this when:
     * - User's project role changes
     * - User is added/removed from project
     * - Role permissions are updated
     *
     * @param userId the user ID
     * @param projectId the project ID
     */
    public void invalidateProjectPermissions(String userId, String projectId) {
        String key = buildProjectKey(userId, projectId);

        try {
            redissonClient.getBucket(key).delete();
            log.info("Invalidated project permissions cache: user='{}', project='{}'", userId, projectId);

        } catch (Exception e) {
            log.error("Failed to invalidate project permissions cache: user='{}', project='{}'", userId, projectId,
                    e);
        }
    }

    /**
     * Invalidate all workspace permissions for a workspace
     *
     * Call this when workspace role permissions are updated
     *
     * @param workspaceId the workspace ID
     */
    public void invalidateAllWorkspacePermissions(String workspaceId) {
        String pattern = WORKSPACE_PERMISSION_PREFIX + "*:" + workspaceId;

        try {
            var keys = redissonClient.getKeys().getKeysByPattern(pattern);
            keys.forEach(key -> redissonClient.getBucket(key).delete());

            log.info("Invalidated all workspace permissions cache for workspace: '{}'", workspaceId);

        } catch (Exception e) {
            log.error("Failed to invalidate all workspace permissions: workspace='{}'", workspaceId, e);
        }
    }

    /**
     * Build cache key for workspace permissions
     */
    private String buildWorkspaceKey(String userId, String workspaceId) {
        return WORKSPACE_PERMISSION_PREFIX + userId + ":" + workspaceId;
    }

    /**
     * Build cache key for project permissions
     */
    private String buildProjectKey(String userId, String projectId) {
        return PROJECT_PERMISSION_PREFIX + userId + ":" + projectId;
    }
}
