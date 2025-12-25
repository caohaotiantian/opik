package com.comet.opik.domain;

import com.comet.opik.infrastructure.usagelimit.Quota;
import com.comet.opik.infrastructure.usagelimit.Quota.QuotaType;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.List;

/**
 * Service for managing workspace quotas
 * Provides quota loading and caching functionality
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class WorkspaceQuotaService {

    private final @NonNull WorkspaceDAO workspaceDAO;
    private final @NonNull SpanDAO spanDAO;
    private final @NonNull RedissonClient redissonClient;

    private static final String QUOTA_CACHE_PREFIX = "workspace:quota:";
    private static final int QUOTA_CACHE_TTL_SECONDS = 60; // 1 minute cache

    /**
     * Load workspace quotas with caching
     *
     * @param workspaceId the workspace ID
     * @return list of quotas
     */
    @WithSpan
    public List<Quota> loadWorkspaceQuotas(@NonNull String workspaceId) {
        log.debug("Loading quotas for workspace: '{}'", workspaceId);

        // 1. Try to get from cache
        String cacheKey = QUOTA_CACHE_PREFIX + workspaceId;
        RBucket<List<Quota>> bucket = redissonClient.getBucket(cacheKey);
        List<Quota> cached = bucket.get();

        if (cached != null) {
            log.debug("Quota cache hit for workspace: '{}'", workspaceId);
            return cached;
        }

        // 2. Load from database
        var workspace = workspaceDAO.findById(workspaceId);
        if (workspace.isEmpty()) {
            log.warn("Workspace not found: '{}'", workspaceId);
            return List.of();
        }

        int quotaLimit = workspace.get().quotaLimit();

        // 3. Get used span count (blocking call on reactive result)
        Integer usedSpanCount;
        try {
            usedSpanCount = spanDAO.countByWorkspaceId(workspaceId).block();
            if (usedSpanCount == null) {
                usedSpanCount = 0;
            }
        } catch (Exception e) {
            log.error("Failed to count spans for workspace: '{}'", workspaceId, e);
            usedSpanCount = 0;
        }

        // 4. Build quota object
        Quota spanQuota = Quota.builder()
                .type(QuotaType.OPIK_SPAN_COUNT)
                .limit(quotaLimit)
                .used(usedSpanCount)
                .build();

        List<Quota> quotas = List.of(spanQuota);

        // 5. Cache result
        try {
            bucket.set(quotas);
            bucket.expire(java.time.Duration.ofSeconds(QUOTA_CACHE_TTL_SECONDS));
            log.debug("Workspace '{}' quota: limit={}, used={}, cached for {}s",
                    workspaceId, quotaLimit, usedSpanCount, QUOTA_CACHE_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("Failed to cache quota for workspace: '{}'", workspaceId, e);
        }

        return quotas;
    }

    /**
     * Invalidate workspace quota cache
     * Should be called after creating/deleting spans
     *
     * @param workspaceId the workspace ID
     */
    @WithSpan
    public void invalidateQuotaCache(@NonNull String workspaceId) {
        String cacheKey = QUOTA_CACHE_PREFIX + workspaceId;
        try {
            redissonClient.getBucket(cacheKey).delete();
            log.debug("Invalidated quota cache for workspace: '{}'", workspaceId);
        } catch (Exception e) {
            log.warn("Failed to invalidate quota cache for workspace: '{}'", workspaceId, e);
        }
    }
}
