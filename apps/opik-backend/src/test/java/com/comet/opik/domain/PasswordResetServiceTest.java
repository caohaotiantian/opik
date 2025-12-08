package com.comet.opik.domain;

import com.comet.opik.api.PasswordResetStatus;
import com.comet.opik.api.PasswordResetToken;
import com.comet.opik.api.User;
import com.comet.opik.api.UserStatus;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.jdbi.v3.core.Handle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.vyarus.guicey.jdbi3.tx.TransactionTemplate;
import ru.vyarus.guicey.jdbi3.tx.TxAction;
import ru.vyarus.guicey.jdbi3.tx.TxConfig;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 密码重置服务单元测试
 *
 * 测试范围：
 * - 请求密码重置（生成令牌）
 * - 使用令牌重置密码
 * - 令牌验证
 * - 过期令牌清理
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetService单元测试")
class PasswordResetServiceTest {

    @Mock
    private PasswordResetTokenDAO tokenDAO;

    @Mock
    private UserDAO userDAO;

    @Mock
    private PasswordService passwordService;

    @Mock
    private IdGenerator idGenerator;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private Handle handle;

    private PasswordResetService resetService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        resetService = new PasswordResetService(tokenDAO, userDAO, passwordService, idGenerator, transactionTemplate);

        // Mock TransactionTemplate to execute the action with our mocked handle
        lenient().when(transactionTemplate.inTransaction(any(TxConfig.class), any(TxAction.class)))
                .thenAnswer(invocation -> {
                    TxAction<Object> action = invocation.getArgument(1);
                    lenient().when(handle.attach(PasswordResetTokenDAO.class)).thenReturn(tokenDAO);
                    lenient().when(handle.attach(UserDAO.class)).thenReturn(userDAO);
                    return action.execute(handle);
                });
    }

    @Nested
    @DisplayName("请求密码重置测试")
    class RequestPasswordResetTests {

        @Test
        @DisplayName("应该成功生成密码重置令牌")
        void shouldGenerateResetTokenSuccessfully() {
            // Given
            String email = "user@example.com";
            String ipAddress = "192.168.1.100";
            String userId = UUID.randomUUID().toString();
            String tokenId = UUID.randomUUID().toString();

            User user = User.builder()
                    .id(userId)
                    .username("testuser")
                    .email(email)
                    .status(UserStatus.ACTIVE)
                    .build();

            when(userDAO.findByEmail(email)).thenReturn(Optional.of(user));
            when(idGenerator.generateId()).thenReturn(UUID.fromString(tokenId));

            // When
            String token = resetService.requestPasswordReset(email, ipAddress);

            // Then
            assertThat(token).isNotNull();
            assertThat(token).isNotEmpty();

            // Verify token was inserted
            ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(tokenDAO).insert(captor.capture());

            PasswordResetToken capturedToken = captor.getValue();
            assertThat(capturedToken.id()).isEqualTo(tokenId);
            assertThat(capturedToken.userId()).isEqualTo(userId);
            assertThat(capturedToken.status()).isEqualTo(PasswordResetStatus.PENDING);
            assertThat(capturedToken.ipAddress()).isEqualTo(ipAddress);
            assertThat(capturedToken.expiresAt()).isAfter(Instant.now());
            assertThat(capturedToken.expiresAt()).isBefore(Instant.now().plus(25, ChronoUnit.HOURS));

            // Verify old tokens were invalidated
            verify(tokenDAO).invalidateAllUserTokens(userId);
        }

        @Test
        @DisplayName("用户不存在时应该返回通用错误（防止邮箱枚举）")
        void shouldReturnGenericErrorWhenUserNotFound() {
            // Given
            String email = "nonexistent@example.com";
            String ipAddress = "192.168.1.100";

            when(userDAO.findByEmail(email)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> resetService.requestPasswordReset(email, ipAddress))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("If an account exists");

            // Should not create token for non-existent user
            verify(tokenDAO, never()).insert(any());
        }

        @Test
        @DisplayName("应该使旧令牌失效后再创建新令牌")
        void shouldInvalidateOldTokensBeforeCreatingNew() {
            // Given
            String email = "user@example.com";
            String userId = UUID.randomUUID().toString();

            User user = User.builder()
                    .id(userId)
                    .email(email)
                    .build();

            when(userDAO.findByEmail(email)).thenReturn(Optional.of(user));
            when(idGenerator.generateId()).thenReturn(UUID.randomUUID());

            // When
            resetService.requestPasswordReset(email, "127.0.0.1");

            // Then - verify invalidation was called before insert
            verify(tokenDAO).invalidateAllUserTokens(userId);
            verify(tokenDAO).insert(any(PasswordResetToken.class));
        }
    }

    @Nested
    @DisplayName("使用令牌重置密码测试")
    class ResetPasswordTests {

        @Test
        @DisplayName("应该成功使用有效令牌重置密码")
        void shouldResetPasswordSuccessfully() {
            // Given
            String token = "valid_reset_token";
            String newPassword = "NewPassword@123";
            String tokenId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String newPasswordHash = "$2a$10$newhash";

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .id(tokenId)
                    .userId(userId)
                    .token(token)
                    .status(PasswordResetStatus.PENDING)
                    .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                    .build();

            User user = User.builder()
                    .id(userId)
                    .username("testuser")
                    .email("user@example.com")
                    .build();

            when(tokenDAO.findValidToken(eq(token), any(Instant.class))).thenReturn(Optional.of(resetToken));
            when(passwordService.isPasswordStrong(newPassword)).thenReturn(true);
            when(userDAO.findById(userId)).thenReturn(Optional.of(user));
            when(passwordService.hashPassword(newPassword)).thenReturn(newPasswordHash);

            // When
            resetService.resetPassword(token, newPassword);

            // Then
            verify(userDAO).updatePassword(userId, newPasswordHash, "system");
            verify(tokenDAO).updateStatus(eq(tokenId), eq(PasswordResetStatus.USED), any(Instant.class), eq("system"));
        }

        @Test
        @DisplayName("应该拒绝无效或过期的令牌")
        void shouldRejectInvalidOrExpiredToken() {
            // Given
            String invalidToken = "invalid_token";
            String newPassword = "NewPassword@123";

            when(tokenDAO.findValidToken(eq(invalidToken), any(Instant.class))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> resetService.resetPassword(invalidToken, newPassword))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid or expired reset token");

            verify(userDAO, never()).updatePassword(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("应该拒绝弱密码")
        void shouldRejectWeakPassword() {
            // Given
            String token = "valid_token";
            String weakPassword = "123456";
            String tokenId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .id(tokenId)
                    .userId(userId)
                    .token(token)
                    .status(PasswordResetStatus.PENDING)
                    .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                    .build();

            when(tokenDAO.findValidToken(eq(token), any(Instant.class))).thenReturn(Optional.of(resetToken));
            when(passwordService.isPasswordStrong(weakPassword)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> resetService.resetPassword(token, weakPassword))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Password must be at least 8 characters");

            verify(userDAO, never()).updatePassword(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("用户不存在时应该抛出异常")
        void shouldThrowExceptionWhenUserNotFound() {
            // Given
            String token = "valid_token";
            String newPassword = "NewPassword@123";
            String userId = UUID.randomUUID().toString();

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .token(token)
                    .status(PasswordResetStatus.PENDING)
                    .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                    .build();

            when(tokenDAO.findValidToken(eq(token), any(Instant.class))).thenReturn(Optional.of(resetToken));
            when(passwordService.isPasswordStrong(newPassword)).thenReturn(true);
            when(userDAO.findById(userId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> resetService.resetPassword(token, newPassword))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("令牌验证测试")
    class ValidateTokenTests {

        @Test
        @DisplayName("应该成功验证有效令牌")
        void shouldValidateValidToken() {
            // Given
            String token = "valid_token";

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .id(UUID.randomUUID().toString())
                    .token(token)
                    .status(PasswordResetStatus.PENDING)
                    .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                    .build();

            when(tokenDAO.findValidToken(eq(token), any(Instant.class))).thenReturn(Optional.of(resetToken));

            // When
            boolean isValid = resetService.validateToken(token);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("应该返回false对于无效令牌")
        void shouldReturnFalseForInvalidToken() {
            // Given
            String invalidToken = "invalid_token";

            when(tokenDAO.findValidToken(eq(invalidToken), any(Instant.class))).thenReturn(Optional.empty());

            // When
            boolean isValid = resetService.validateToken(invalidToken);

            // Then
            assertThat(isValid).isFalse();
        }
    }

    @Nested
    @DisplayName("过期令牌清理测试")
    class CleanupExpiredTokensTests {

        @Test
        @DisplayName("应该成功清理过期令牌")
        void shouldCleanupExpiredTokens() {
            // Given
            int deletedCount = 5;
            when(tokenDAO.deleteExpired(any(Instant.class))).thenReturn(deletedCount);

            // When
            int result = resetService.cleanupExpiredTokens();

            // Then
            assertThat(result).isEqualTo(deletedCount);
            verify(tokenDAO).deleteExpired(any(Instant.class));
        }

        @Test
        @DisplayName("没有过期令牌时应该返回0")
        void shouldReturnZeroWhenNoExpiredTokens() {
            // Given
            when(tokenDAO.deleteExpired(any(Instant.class))).thenReturn(0);

            // When
            int result = resetService.cleanupExpiredTokens();

            // Then
            assertThat(result).isZero();
        }
    }

    @Nested
    @DisplayName("令牌安全性测试")
    class TokenSecurityTests {

        @Test
        @DisplayName("生成的令牌应该是唯一的")
        void shouldGenerateUniqueTokens() {
            // Given
            String email = "user@example.com";
            String userId = UUID.randomUUID().toString();

            User user = User.builder()
                    .id(userId)
                    .email(email)
                    .build();

            when(userDAO.findByEmail(email)).thenReturn(Optional.of(user));
            when(idGenerator.generateId())
                    .thenReturn(UUID.randomUUID())
                    .thenReturn(UUID.randomUUID());

            // When
            String token1 = resetService.requestPasswordReset(email, "127.0.0.1");
            String token2 = resetService.requestPasswordReset(email, "127.0.0.1");

            // Then
            assertThat(token1).isNotEqualTo(token2);
        }

        @Test
        @DisplayName("生成的令牌应该足够长")
        void shouldGenerateSufficientlyLongToken() {
            // Given
            String email = "user@example.com";
            String userId = UUID.randomUUID().toString();

            User user = User.builder()
                    .id(userId)
                    .email(email)
                    .build();

            when(userDAO.findByEmail(email)).thenReturn(Optional.of(user));
            when(idGenerator.generateId()).thenReturn(UUID.randomUUID());

            // When
            String token = resetService.requestPasswordReset(email, "127.0.0.1");

            // Then
            // Base64 encoded 32 bytes should be around 43 characters
            assertThat(token.length()).isGreaterThanOrEqualTo(40);
        }

        @Test
        @DisplayName("令牌过期时间应该是24小时")
        void shouldHave24HourExpiry() {
            // Given
            String email = "user@example.com";
            String userId = UUID.randomUUID().toString();

            User user = User.builder()
                    .id(userId)
                    .email(email)
                    .build();

            when(userDAO.findByEmail(email)).thenReturn(Optional.of(user));
            when(idGenerator.generateId()).thenReturn(UUID.randomUUID());

            // When
            resetService.requestPasswordReset(email, "127.0.0.1");

            // Then
            ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(tokenDAO).insert(captor.capture());

            PasswordResetToken capturedToken = captor.getValue();
            Instant expectedExpiry = Instant.now().plus(24, ChronoUnit.HOURS);

            // Allow 5 seconds tolerance
            assertThat(capturedToken.expiresAt())
                    .isBetween(expectedExpiry.minusSeconds(5), expectedExpiry.plusSeconds(5));
        }
    }
}
