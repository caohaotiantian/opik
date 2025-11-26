package com.comet.opik.domain;

import com.comet.opik.api.ApiKey;
import com.comet.opik.api.ApiKeyStatus;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * API Key服务单元测试
 *
 * 测试范围：
 * - API Key生成
 * - API Key验证
 * - API Key撤销
 * - API Key数量限制
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyService单元测试")
class ApiKeyServiceTest {

    @Mock
    private ApiKeyDAO apiKeyDAO;

    @Mock
    private IdGenerator idGenerator;

    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(apiKeyDAO, idGenerator);
    }

    @Nested
    @DisplayName("API Key生成测试")
    class GenerateApiKeyTests {

        @Test
        @DisplayName("应该成功生成API Key")
        void shouldGenerateApiKeySuccessfully() {
            // Given
            String userId = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();
            String name = "Test API Key";
            String description = "Test Description";
            Set<String> scopes = Set.of("read", "write");
            Integer expiryDays = 30;
            String apiKeyId = UUID.randomUUID().toString();

            when(apiKeyDAO.countActiveByUser(userId)).thenReturn(5);
            when(idGenerator.generateId()).thenReturn(UUID.fromString(apiKeyId));

            // When
            ApiKeyService.ApiKeyResult result = apiKeyService.generateApiKey(
                    userId, workspaceId, name, description, scopes, expiryDays);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.plainApiKey()).isNotNull();
            assertThat(result.plainApiKey()).startsWith("opik_");
            assertThat(result.apiKey()).isNotNull();
            assertThat(result.apiKey().id()).isEqualTo(apiKeyId);
            assertThat(result.apiKey().userId()).isEqualTo(userId);
            assertThat(result.apiKey().workspaceId()).isEqualTo(workspaceId);
            assertThat(result.apiKey().name()).isEqualTo(name);
            assertThat(result.apiKey().description()).isEqualTo(description);
            assertThat(result.apiKey().scopes()).isEqualTo(scopes);
            assertThat(result.apiKey().status()).isEqualTo(ApiKeyStatus.ACTIVE);
            assertThat(result.apiKey().expiresAt()).isNotNull();
            assertThat(result.apiKey().expiresAt()).isAfter(Instant.now());

            // Verify interactions
            verify(apiKeyDAO).countActiveByUser(userId);
            verify(apiKeyDAO).insert(any(ApiKey.class));
        }

        @Test
        @DisplayName("应该生成永不过期的API Key")
        void shouldGenerateNonExpiringApiKey() {
            // Given
            String userId = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();
            String apiKeyId = UUID.randomUUID().toString();

            when(apiKeyDAO.countActiveByUser(userId)).thenReturn(5);
            when(idGenerator.generateId()).thenReturn(UUID.fromString(apiKeyId));

            // When
            ApiKeyService.ApiKeyResult result = apiKeyService.generateApiKey(
                    userId, workspaceId, "Test Key", null, null, null);

            // Then
            assertThat(result.apiKey().expiresAt()).isNull();
        }

        @Test
        @DisplayName("API Key应该包含正确的前缀")
        void shouldGenerateApiKeyWithCorrectPrefix() {
            // Given
            String userId = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();

            when(apiKeyDAO.countActiveByUser(userId)).thenReturn(0);
            when(idGenerator.generateId()).thenReturn(UUID.randomUUID());

            // When
            ApiKeyService.ApiKeyResult result = apiKeyService.generateApiKey(
                    userId, workspaceId, "Test Key", null, null, null);

            // Then
            assertThat(result.plainApiKey()).startsWith("opik_");
            assertThat(result.apiKey().keyPrefix()).startsWith("opik_");
        }

        @Test
        @DisplayName("应该拒绝超过数量限制的API Key生成")
        void shouldRejectWhenApiKeyLimitReached() {
            // Given
            String userId = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();

            when(apiKeyDAO.countActiveByUser(userId)).thenReturn(50); // At limit

            // When & Then
            assertThatThrownBy(() -> apiKeyService.generateApiKey(
                    userId, workspaceId, "Test Key", null, null, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Maximum API keys limit reached");

            verify(apiKeyDAO, never()).insert(any());
        }

        @Test
        @DisplayName("生成的API Key应该存储哈希值而非明文")
        void shouldStoreHashedApiKey() {
            // Given
            String userId = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();

            when(apiKeyDAO.countActiveByUser(userId)).thenReturn(0);
            when(idGenerator.generateId()).thenReturn(UUID.randomUUID());

            // When
            ApiKeyService.ApiKeyResult result = apiKeyService.generateApiKey(
                    userId, workspaceId, "Test Key", null, null, null);

            // Then
            ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
            verify(apiKeyDAO).insert(captor.capture());

            ApiKey capturedKey = captor.getValue();
            assertThat(capturedKey.keyHash()).isNotEqualTo(result.plainApiKey());
            assertThat(capturedKey.keyHash()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("API Key验证测试")
    class ValidateApiKeyTests {

        @Test
        @DisplayName("应该成功验证有效的API Key")
        void shouldValidateValidApiKey() {
            // Given
            String apiKey = "opik_test_key_12345678";
            String apiKeyId = UUID.randomUUID().toString();

            ApiKey apiKeyEntity = ApiKey.builder()
                    .id(apiKeyId)
                    .userId(UUID.randomUUID().toString())
                    .workspaceId(UUID.randomUUID().toString())
                    .status(ApiKeyStatus.ACTIVE)
                    .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                    .build();

            when(apiKeyDAO.findByKeyHash(anyString())).thenReturn(Optional.of(apiKeyEntity));

            // When
            Optional<ApiKey> result = apiKeyService.validateApiKey(apiKey);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(apiKeyEntity);
            verify(apiKeyDAO).findByKeyHash(anyString());
        }

        @Test
        @DisplayName("应该拒绝不存在的API Key")
        void shouldRejectNonExistentApiKey() {
            // Given
            String apiKey = "opik_nonexistent_key";

            when(apiKeyDAO.findByKeyHash(anyString())).thenReturn(Optional.empty());

            // When
            Optional<ApiKey> result = apiKeyService.validateApiKey(apiKey);

            // Then
            assertThat(result).isEmpty();
            verify(apiKeyDAO).findByKeyHash(anyString());
        }

        @Test
        @DisplayName("应该拒绝已撤销的API Key")
        void shouldRejectRevokedApiKey() {
            // Given
            String apiKey = "opik_revoked_key";

            ApiKey revokedKey = ApiKey.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(UUID.randomUUID().toString())
                    .workspaceId(UUID.randomUUID().toString())
                    .status(ApiKeyStatus.REVOKED)
                    .build();

            when(apiKeyDAO.findByKeyHash(anyString())).thenReturn(Optional.of(revokedKey));

            // When
            Optional<ApiKey> result = apiKeyService.validateApiKey(apiKey);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("应该拒绝已过期的API Key")
        void shouldRejectExpiredApiKey() {
            // Given
            String apiKey = "opik_expired_key";

            ApiKey expiredKey = ApiKey.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(UUID.randomUUID().toString())
                    .workspaceId(UUID.randomUUID().toString())
                    .status(ApiKeyStatus.ACTIVE)
                    .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .build();

            when(apiKeyDAO.findByKeyHash(anyString())).thenReturn(Optional.of(expiredKey));

            // When
            Optional<ApiKey> result = apiKeyService.validateApiKey(apiKey);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("应该接受没有过期时间的API Key")
        void shouldAcceptNonExpiringApiKey() {
            // Given
            String apiKey = "opik_non_expiring_key";

            ApiKey nonExpiringKey = ApiKey.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(UUID.randomUUID().toString())
                    .workspaceId(UUID.randomUUID().toString())
                    .status(ApiKeyStatus.ACTIVE)
                    .expiresAt(null)
                    .build();

            when(apiKeyDAO.findByKeyHash(anyString())).thenReturn(Optional.of(nonExpiringKey));

            // When
            Optional<ApiKey> result = apiKeyService.validateApiKey(apiKey);

            // Then
            assertThat(result).isPresent();
        }
    }

    @Nested
    @DisplayName("API Key撤销测试")
    class RevokeApiKeyTests {

        @Test
        @DisplayName("应该成功撤销API Key")
        void shouldRevokeApiKeySuccessfully() {
            // Given
            String apiKeyId = UUID.randomUUID().toString();
            String revokedBy = "admin";

            ApiKey apiKey = ApiKey.builder()
                    .id(apiKeyId)
                    .userId(UUID.randomUUID().toString())
                    .workspaceId(UUID.randomUUID().toString())
                    .status(ApiKeyStatus.ACTIVE)
                    .build();

            when(apiKeyDAO.findById(apiKeyId)).thenReturn(Optional.of(apiKey));

            // When
            apiKeyService.revokeApiKey(apiKeyId, revokedBy);

            // Then
            verify(apiKeyDAO).updateStatus(apiKeyId, ApiKeyStatus.REVOKED, revokedBy);
        }

        @Test
        @DisplayName("API Key不存在时应该抛出异常")
        void shouldThrowExceptionWhenApiKeyNotFound() {
            // Given
            String apiKeyId = UUID.randomUUID().toString();
            when(apiKeyDAO.findById(apiKeyId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> apiKeyService.revokeApiKey(apiKeyId, "admin"))
                    .isInstanceOf(NotFoundException.class);

            verify(apiKeyDAO, never()).updateStatus(anyString(), any(), anyString());
        }

        @Test
        @DisplayName("撤销已撤销的API Key应该直接返回")
        void shouldNotThrowWhenRevokingAlreadyRevokedKey() {
            // Given
            String apiKeyId = UUID.randomUUID().toString();

            ApiKey revokedKey = ApiKey.builder()
                    .id(apiKeyId)
                    .userId(UUID.randomUUID().toString())
                    .workspaceId(UUID.randomUUID().toString())
                    .status(ApiKeyStatus.REVOKED)
                    .build();

            when(apiKeyDAO.findById(apiKeyId)).thenReturn(Optional.of(revokedKey));

            // When
            apiKeyService.revokeApiKey(apiKeyId, "admin");

            // Then - Should not update status again
            verify(apiKeyDAO, never()).updateStatus(anyString(), any(), anyString());
        }
    }

    @Nested
    @DisplayName("API Key更新测试")
    class UpdateLastUsedTests {

        @Test
        @DisplayName("应该异步更新最后使用时间")
        void shouldUpdateLastUsedAsyncSuccessfully() throws InterruptedException {
            // Given
            String apiKeyId = UUID.randomUUID().toString();

            // When
            apiKeyService.updateLastUsedAsync(apiKeyId);

            // Wait for async operation
            Thread.sleep(100);

            // Then
            verify(apiKeyDAO).updateLastUsed(eq(apiKeyId), any(Instant.class));
        }
    }

    @Nested
    @DisplayName("API Key生成安全性测试")
    class ApiKeySecurityTests {

        @Test
        @DisplayName("生成的API Key应该是唯一的")
        void shouldGenerateUniqueApiKeys() {
            // Given
            String userId = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();

            when(apiKeyDAO.countActiveByUser(userId)).thenReturn(0);
            when(idGenerator.generateId())
                    .thenReturn(UUID.randomUUID())
                    .thenReturn(UUID.randomUUID());

            // When
            ApiKeyService.ApiKeyResult key1 = apiKeyService.generateApiKey(
                    userId, workspaceId, "Key 1", null, null, null);
            ApiKeyService.ApiKeyResult key2 = apiKeyService.generateApiKey(
                    userId, workspaceId, "Key 2", null, null, null);

            // Then
            assertThat(key1.plainApiKey()).isNotEqualTo(key2.plainApiKey());
            assertThat(key1.apiKey().keyHash()).isNotEqualTo(key2.apiKey().keyHash());
        }

        @Test
        @DisplayName("API Key应该足够长且随机")
        void shouldGenerateSufficientlyLongApiKey() {
            // Given
            String userId = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();

            when(apiKeyDAO.countActiveByUser(userId)).thenReturn(0);
            when(idGenerator.generateId()).thenReturn(UUID.randomUUID());

            // When
            ApiKeyService.ApiKeyResult result = apiKeyService.generateApiKey(
                    userId, workspaceId, "Test Key", null, null, null);

            // Then
            // opik_ prefix (5 chars) + base64 encoded random bytes (should be >50 chars total)
            assertThat(result.plainApiKey().length()).isGreaterThan(50);
        }

        @Test
        @DisplayName("API Key哈希应该使用SHA-256")
        void shouldUseSecureHashForApiKey() {
            // Given
            String userId = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();

            when(apiKeyDAO.countActiveByUser(userId)).thenReturn(0);
            when(idGenerator.generateId()).thenReturn(UUID.randomUUID());

            // When
            ApiKeyService.ApiKeyResult result = apiKeyService.generateApiKey(
                    userId, workspaceId, "Test Key", null, null, null);

            // Then
            ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
            verify(apiKeyDAO).insert(captor.capture());

            ApiKey capturedKey = captor.getValue();
            // SHA-256 Base64 encoded hash should be 44 characters
            assertThat(capturedKey.keyHash()).hasSize(44);
        }
    }

    @Nested
    @DisplayName("API Key过期时间测试")
    class ApiKeyExpiryTests {

        @Test
        @DisplayName("应该正确计算30天后的过期时间")
        void shouldCalculate30DaysExpiry() {
            // Given
            String userId = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();
            Integer expiryDays = 30;

            when(apiKeyDAO.countActiveByUser(userId)).thenReturn(0);
            when(idGenerator.generateId()).thenReturn(UUID.randomUUID());

            Instant before = Instant.now().plus(expiryDays, ChronoUnit.DAYS).minusSeconds(5);
            Instant after = Instant.now().plus(expiryDays, ChronoUnit.DAYS).plusSeconds(5);

            // When
            ApiKeyService.ApiKeyResult result = apiKeyService.generateApiKey(
                    userId, workspaceId, "Test Key", null, null, expiryDays);

            // Then
            assertThat(result.apiKey().expiresAt()).isBetween(before, after);
        }

        @Test
        @DisplayName("应该正确计算90天后的过期时间")
        void shouldCalculate90DaysExpiry() {
            // Given
            String userId = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();
            Integer expiryDays = 90;

            when(apiKeyDAO.countActiveByUser(userId)).thenReturn(0);
            when(idGenerator.generateId()).thenReturn(UUID.randomUUID());

            Instant before = Instant.now().plus(expiryDays, ChronoUnit.DAYS).minusSeconds(5);
            Instant after = Instant.now().plus(expiryDays, ChronoUnit.DAYS).plusSeconds(5);

            // When
            ApiKeyService.ApiKeyResult result = apiKeyService.generateApiKey(
                    userId, workspaceId, "Test Key", null, null, expiryDays);

            // Then
            assertThat(result.apiKey().expiresAt()).isBetween(before, after);
        }
    }

}
