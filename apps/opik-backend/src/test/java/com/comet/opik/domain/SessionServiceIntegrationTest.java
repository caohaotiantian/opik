package com.comet.opik.domain;

import com.comet.opik.api.Session;
import com.comet.opik.api.resources.utils.ClickHouseContainerUtils;
import com.comet.opik.api.resources.utils.MigrationUtils;
import com.comet.opik.api.resources.utils.MySQLContainerUtils;
import com.comet.opik.api.resources.utils.RedisContainerUtils;
import com.comet.opik.api.resources.utils.TestDropwizardAppExtensionUtils;
import com.comet.opik.extensions.DropwizardAppExtensionProvider;
import com.comet.opik.extensions.RegisterApp;
import com.redis.testcontainers.RedisContainer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.redisson.api.RedissonClient;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mysql.MySQLContainer;
import ru.vyarus.dropwizard.guice.test.jupiter.ext.TestDropwizardAppExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for SessionService
 * Tests session management with real MySQL and Redis containers
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DropwizardAppExtensionProvider.class)
class SessionServiceIntegrationTest {

    private final RedisContainer REDIS = RedisContainerUtils.newRedisContainer();
    private final MySQLContainer MYSQL = MySQLContainerUtils.newMySQLContainer();
    private final GenericContainer<?> ZOOKEEPER_CONTAINER = ClickHouseContainerUtils.newZookeeperContainer();
    private final ClickHouseContainer CLICKHOUSE = ClickHouseContainerUtils.newClickHouseContainer(ZOOKEEPER_CONTAINER);

    @RegisterApp
    private final TestDropwizardAppExtension APP;

    {
        Startables.deepStart(REDIS, MYSQL, CLICKHOUSE, ZOOKEEPER_CONTAINER).join();
        var databaseAnalyticsFactory = ClickHouseContainerUtils.newDatabaseAnalyticsFactory(CLICKHOUSE,
                ClickHouseContainerUtils.DATABASE_NAME);

        // Run database migrations
        MigrationUtils.runMysqlDbMigration(MYSQL);
        MigrationUtils.runClickhouseDbMigration(CLICKHOUSE);

        APP = TestDropwizardAppExtensionUtils.newTestDropwizardAppExtension(
                MYSQL.getJdbcUrl(),
                databaseAnalyticsFactory,
                null,
                REDIS.getRedisURI());
    }

    @AfterEach
    void tearDown(RedissonClient redissonClient) {
        // Clean up test data
        try {
            // Clear Redis cache
            redissonClient.getKeys().flushdb();

            // Note: In production, you might want to delete specific test sessions
            // For simplicity in tests, we're clearing the entire DB
        } catch (Exception e) {
            log.warn("Failed to clean up test data", e);
        }
    }

    /**
     * Helper method to create a test user
     */
    private void createTestUser(UserDAO userDAO, String userId, String username) {
        var user = com.comet.opik.api.User.builder()
                .id(userId)
                .username(username)
                .email(username + "@test.com")
                .passwordHash("$2a$10$hashedpassword")
                .fullName("Test User")
                .status(com.comet.opik.api.UserStatus.ACTIVE)
                .systemAdmin(false)
                .emailVerified(true)
                .locale("en-US")
                .version(0)
                .createdAt(Instant.now())
                .createdBy("system")
                .lastUpdatedAt(Instant.now())
                .lastUpdatedBy("system")
                .build();
        userDAO.insert(user);
    }

    @Test
    void shouldCreateSession_andCacheToRedis(SessionService sessionService, UserDAO userDAO) {
        // Given - Create test user first
        String userId = "test-user-1";
        createTestUser(userDAO, userId, "testuser1");

        String ipAddress = "192.168.1.100";
        String userAgent = "Mozilla/5.0 Test Browser";

        // When
        Session created = sessionService.createSession(userId, ipAddress, userAgent);

        // Then
        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.userId()).isEqualTo(userId);
        assertThat(created.sessionToken()).isNotBlank(); // Plain token returned on creation
        assertThat(created.ipAddress()).isEqualTo(ipAddress);
        assertThat(created.userAgent()).isEqualTo(userAgent);
        assertThat(created.expiresAt()).isAfter(Instant.now());
        assertThat(created.fingerprint()).isNotBlank();

        // Verify session is cached by validating it immediately
        // (validation will use cache if available)
        var validated = sessionService.validateSession(created.sessionToken(), ipAddress, userAgent);
        assertThat(validated).as("Session should be retrievable immediately after creation").isPresent();
        assertThat(validated.get().id()).isEqualTo(created.id());
    }

    @Test
    void shouldValidateSession_fromCache(SessionService sessionService, UserDAO userDAO) {
        // Given - Create test user first
        String userId = "test-user-2";
        createTestUser(userDAO, userId, "testuser2");

        String ipAddress = "192.168.1.101";
        String userAgent = "Mozilla/5.0 Test Browser";
        Session created = sessionService.createSession(userId, ipAddress, userAgent);
        String plainToken = created.sessionToken();

        // When - Validate session with same IP and user agent
        var validated = sessionService.validateSession(plainToken, ipAddress, userAgent);

        // Then
        assertThat(validated).isPresent();
        assertThat(validated.get().userId()).isEqualTo(userId);
        assertThat(validated.get().ipAddress()).isEqualTo(ipAddress);
    }

    @Test
    void shouldValidateSession_fromDatabase_whenCacheMiss(SessionService sessionService,
            RedissonClient redissonClient, UserDAO userDAO) {
        // Given - Create test user first
        String userId = "test-user-3";
        createTestUser(userDAO, userId, "testuser3");

        String ipAddress = "192.168.1.102";
        String userAgent = "Mozilla/5.0 Test Browser";
        Session created = sessionService.createSession(userId, ipAddress, userAgent);
        String plainToken = created.sessionToken();

        // Manually remove from cache to simulate cache miss
        String cacheKey = "session:" + plainToken;
        redissonClient.getBucket(cacheKey).delete();

        // When - Validate session (should load from database)
        var validated = sessionService.validateSession(plainToken, ipAddress, userAgent);

        // Then
        assertThat(validated).isPresent();
        assertThat(validated.get().userId()).isEqualTo(userId);

        // Verify session was re-cached by validating again (should be fast from cache)
        var validated2 = sessionService.validateSession(plainToken, ipAddress, userAgent);
        assertThat(validated2).isPresent();
    }

    @Test
    void shouldRejectSession_whenFingerprintMismatch(SessionService sessionService, RedissonClient redissonClient,
            UserDAO userDAO) {
        // Given - Create test user first
        String userId = "test-user-4";
        createTestUser(userDAO, userId, "testuser4");

        String originalIp = "192.168.1.103";
        String originalUserAgent = "Mozilla/5.0 Test Browser";
        Session created = sessionService.createSession(userId, originalIp, originalUserAgent);
        String plainToken = created.sessionToken();

        // When - Try to validate with different IP
        String differentIp = "192.168.1.999";
        var validated = sessionService.validateSession(plainToken, differentIp, originalUserAgent);

        // Then
        assertThat(validated).isEmpty();

        // Verify session cannot be validated again with different IP (still invalid)
        var validated2 = sessionService.validateSession(plainToken, differentIp, originalUserAgent);
        assertThat(validated2).isEmpty();
    }

    @Test
    void shouldRejectSession_whenExpired(SessionService sessionService, SessionDAO sessionDAO,
            RedissonClient redissonClient, UserDAO userDAO) {
        // Given - Create test user first
        String userId = "test-user-5";
        createTestUser(userDAO, userId, "testuser5");

        String ipAddress = "192.168.1.104";
        String userAgent = "Mozilla/5.0 Test Browser";

        // Create expired session entity
        Instant expiredTime = Instant.now().minus(1, ChronoUnit.HOURS);
        String sessionToken = "expired-session-token";
        Session expiredSession = Session.builder()
                .id(java.util.UUID.randomUUID().toString())
                .userId(userId)
                .sessionToken(sessionToken)
                .fingerprint("test-fingerprint")
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .expiresAt(expiredTime)
                .lastActivityAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .version(0)
                .createdAt(Instant.now().minus(3, ChronoUnit.HOURS))
                .createdBy("system")
                .lastUpdatedAt(Instant.now().minus(3, ChronoUnit.HOURS))
                .lastUpdatedBy("system")
                .build();
        sessionDAO.insert(expiredSession);

        // When - Try to validate expired session
        var validated = sessionService.validateSession(sessionToken, ipAddress, userAgent);

        // Then
        assertThat(validated).isEmpty();
    }

    @Test
    void shouldInvalidateSession(SessionService sessionService, RedissonClient redissonClient, UserDAO userDAO) {
        // Given - Create test user first
        String userId = "test-user-6";
        createTestUser(userDAO, userId, "testuser6");

        String ipAddress = "192.168.1.105";
        String userAgent = "Mozilla/5.0 Test Browser";
        Session created = sessionService.createSession(userId, ipAddress, userAgent);
        String plainToken = created.sessionToken();

        // When - Invalidate the session
        sessionService.invalidateSession(plainToken);

        // Then - Session should not be found
        var validated = sessionService.validateSession(plainToken, ipAddress, userAgent);
        assertThat(validated).isEmpty();

        // Verify session cannot be validated again (still invalid)
        var validated2 = sessionService.validateSession(plainToken, ipAddress, userAgent);
        assertThat(validated2).isEmpty();
    }

    @Test
    void shouldCleanupExpiredSessions(SessionService sessionService, SessionDAO sessionDAO, UserDAO userDAO) {
        // Given - Create test user first
        String userId = "test-user-7";
        createTestUser(userDAO, userId, "testuser7");

        // Create an expired session manually
        Session expiredSession = Session.builder()
                .id(java.util.UUID.randomUUID().toString())
                .userId(userId)
                .sessionToken("hashed-token-expired")
                .fingerprint("fingerprint")
                .ipAddress("192.168.1.106")
                .userAgent("Test")
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .lastActivityAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .version(0)
                .createdAt(Instant.now().minus(3, ChronoUnit.HOURS))
                .createdBy("system")
                .lastUpdatedAt(Instant.now().minus(3, ChronoUnit.HOURS))
                .lastUpdatedBy("system")
                .build();
        sessionDAO.insert(expiredSession);

        // Create a valid session
        sessionService.createSession(userId, "192.168.1.107", "Test Browser");

        // When - Run cleanup
        int deleted = sessionService.cleanupExpiredSessions();

        // Then - Should have deleted the expired session
        assertThat(deleted).isGreaterThan(0);

        // Verify expired session is gone
        var found = sessionDAO.findByTokenHash("hashed-token-expired");
        assertThat(found).isEmpty();
    }

    @Test
    void shouldEnforceMaxConcurrentSessions(SessionService sessionService, SessionDAO sessionDAO, UserDAO userDAO) {
        // Given - Create test user first
        String userId = "test-user-8";
        createTestUser(userDAO, userId, "testuser8");

        // Max concurrent sessions is 5
        int maxSessions = 5;

        // Create 6 sessions to exceed the limit
        for (int i = 0; i < maxSessions + 1; i++) {
            String ipAddress = "192.168.1." + (110 + i);
            String userAgent = "Test Browser " + i;
            sessionService.createSession(userId, ipAddress, userAgent);

            // Small delay to ensure different creation times
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Then - Should have exactly maxSessions active sessions
        int activeCount = sessionDAO.countActiveByUser(userId);
        assertThat(activeCount).isEqualTo(maxSessions);
    }

    @Test
    void shouldReturnEmptyForNonExistentSession(SessionService sessionService) {
        // Given
        String nonExistentToken = "non-existent-token-12345";
        String ipAddress = "192.168.1.120";
        String userAgent = "Test Browser";

        // When
        var validated = sessionService.validateSession(nonExistentToken, ipAddress, userAgent);

        // Then
        assertThat(validated).isEmpty();
    }
}
