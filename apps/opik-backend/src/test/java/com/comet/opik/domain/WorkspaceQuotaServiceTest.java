package com.comet.opik.domain;

import com.comet.opik.api.Workspace;
import com.comet.opik.api.WorkspaceStatus;
import com.comet.opik.infrastructure.usagelimit.Quota;
import com.comet.opik.infrastructure.usagelimit.Quota.QuotaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 工作空间配额服务单元测试
 *
 * 测试范围：
 * - 配额加载（成功、失败、缓存）
 * - 配额缓存失效
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkspaceQuotaService单元测试")
class WorkspaceQuotaServiceTest {

    @Mock
    private WorkspaceDAO workspaceDAO;

    @Mock
    private SpanDAO spanDAO;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<List<Quota>> rBucket;

    private WorkspaceQuotaService quotaService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        quotaService = new WorkspaceQuotaService(workspaceDAO, spanDAO, redissonClient);

        // Setup Redis bucket mock
        when(redissonClient.getBucket(anyString())).thenReturn((RBucket) rBucket);
    }

    @Nested
    @DisplayName("加载工作空间配额测试")
    class LoadWorkspaceQuotasTests {

        @Test
        @DisplayName("应该成功加载工作空间配额")
        void shouldLoadWorkspaceQuotasSuccessfully() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            int quotaLimit = 1000;
            int usedSpanCount = 500;

            Workspace workspace = Workspace.builder()
                    .id(workspaceId)
                    .name("test-workspace")
                    .displayName("Test Workspace")
                    .quotaLimit(quotaLimit)
                    .status(WorkspaceStatus.ACTIVE)
                    .build();

            when(rBucket.get()).thenReturn(null); // Cache miss
            when(workspaceDAO.findById(workspaceId)).thenReturn(Optional.of(workspace));
            when(spanDAO.countByWorkspaceId(workspaceId)).thenReturn(Mono.just(usedSpanCount));

            // When
            List<Quota> quotas = quotaService.loadWorkspaceQuotas(workspaceId);

            // Then
            assertThat(quotas).hasSize(1);
            assertThat(quotas.get(0).type()).isEqualTo(QuotaType.OPIK_SPAN_COUNT);
            assertThat(quotas.get(0).limit()).isEqualTo(quotaLimit);
            assertThat(quotas.get(0).used()).isEqualTo(usedSpanCount);

            verify(workspaceDAO).findById(workspaceId);
            verify(spanDAO).countByWorkspaceId(workspaceId);
            verify(rBucket).set(any());
            verify(rBucket).expire(any(java.time.Duration.class));
        }

        @Test
        @DisplayName("应该从缓存中加载配额")
        void shouldLoadQuotasFromCache() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            Quota cachedQuota = Quota.builder()
                    .type(QuotaType.OPIK_SPAN_COUNT)
                    .limit(1000)
                    .used(500)
                    .build();
            List<Quota> cachedQuotas = List.of(cachedQuota);

            when(rBucket.get()).thenReturn(cachedQuotas); // Cache hit

            // When
            List<Quota> quotas = quotaService.loadWorkspaceQuotas(workspaceId);

            // Then
            assertThat(quotas).isEqualTo(cachedQuotas);

            verify(rBucket).get();
            verify(workspaceDAO, never()).findById(anyString());
            verify(spanDAO, never()).countByWorkspaceId(anyString());
        }

        @Test
        @DisplayName("工作空间不存在时应该返回空配额列表")
        void shouldReturnEmptyWhenWorkspaceNotFound() {
            // Given
            String workspaceId = UUID.randomUUID().toString();

            when(rBucket.get()).thenReturn(null); // Cache miss
            when(workspaceDAO.findById(workspaceId)).thenReturn(Optional.empty());

            // When
            List<Quota> quotas = quotaService.loadWorkspaceQuotas(workspaceId);

            // Then
            assertThat(quotas).isEmpty();

            verify(workspaceDAO).findById(workspaceId);
            verify(spanDAO, never()).countByWorkspaceId(anyString());
            verify(rBucket, never()).set(any());
        }

        @Test
        @DisplayName("统计span数量失败时应该使用0")
        void shouldUseZeroWhenCountFails() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            int quotaLimit = 1000;

            Workspace workspace = Workspace.builder()
                    .id(workspaceId)
                    .name("test-workspace")
                    .displayName("Test Workspace")
                    .quotaLimit(quotaLimit)
                    .status(WorkspaceStatus.ACTIVE)
                    .build();

            when(rBucket.get()).thenReturn(null); // Cache miss
            when(workspaceDAO.findById(workspaceId)).thenReturn(Optional.of(workspace));
            when(spanDAO.countByWorkspaceId(workspaceId))
                    .thenReturn(Mono.error(new RuntimeException("Database error")));

            // When
            List<Quota> quotas = quotaService.loadWorkspaceQuotas(workspaceId);

            // Then
            assertThat(quotas).hasSize(1);
            assertThat(quotas.get(0).type()).isEqualTo(QuotaType.OPIK_SPAN_COUNT);
            assertThat(quotas.get(0).limit()).isEqualTo(quotaLimit);
            assertThat(quotas.get(0).used()).isEqualTo(0);

            verify(workspaceDAO).findById(workspaceId);
            verify(spanDAO).countByWorkspaceId(workspaceId);
        }

        @Test
        @DisplayName("缓存失败时不应该影响配额加载")
        void shouldNotFailWhenCacheFails() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            int quotaLimit = 1000;
            int usedSpanCount = 500;

            Workspace workspace = Workspace.builder()
                    .id(workspaceId)
                    .name("test-workspace")
                    .displayName("Test Workspace")
                    .quotaLimit(quotaLimit)
                    .status(WorkspaceStatus.ACTIVE)
                    .build();

            when(rBucket.get()).thenReturn(null); // Cache miss
            when(workspaceDAO.findById(workspaceId)).thenReturn(Optional.of(workspace));
            when(spanDAO.countByWorkspaceId(workspaceId)).thenReturn(Mono.just(usedSpanCount));
            doThrow(new RuntimeException("Redis error")).when(rBucket).set(any());

            // When
            List<Quota> quotas = quotaService.loadWorkspaceQuotas(workspaceId);

            // Then
            assertThat(quotas).hasSize(1);
            assertThat(quotas.get(0).type()).isEqualTo(QuotaType.OPIK_SPAN_COUNT);
            assertThat(quotas.get(0).limit()).isEqualTo(quotaLimit);
            assertThat(quotas.get(0).used()).isEqualTo(usedSpanCount);

            verify(workspaceDAO).findById(workspaceId);
            verify(spanDAO).countByWorkspaceId(workspaceId);
        }
    }

    @Nested
    @DisplayName("配额缓存失效测试")
    class InvalidateQuotaCacheTests {

        @Test
        @DisplayName("应该成功清除配额缓存")
        void shouldInvalidateQuotaCacheSuccessfully() {
            // Given
            String workspaceId = UUID.randomUUID().toString();

            // When
            quotaService.invalidateQuotaCache(workspaceId);

            // Then
            verify(rBucket).delete();
        }

        @Test
        @DisplayName("缓存失效失败时不应该抛出异常")
        void shouldNotThrowWhenInvalidateFails() {
            // Given
            String workspaceId = UUID.randomUUID().toString();
            doThrow(new RuntimeException("Redis error")).when(rBucket).delete();

            // When & Then (should not throw)
            quotaService.invalidateQuotaCache(workspaceId);

            verify(rBucket).delete();
        }
    }
}
