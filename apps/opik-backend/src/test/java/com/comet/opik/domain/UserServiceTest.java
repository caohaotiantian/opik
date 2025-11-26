package com.comet.opik.domain;

import com.comet.opik.api.Session;
import com.comet.opik.api.User;
import com.comet.opik.api.UserStatus;
import com.comet.opik.api.error.ConflictException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
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
 * 用户服务单元测试
 *
 * 测试范围：
 * - 用户注册流程
 * - 用户登录流程
 * - 用户资料更新
 * - 密码修改
 * - 用户删除
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService单元测试")
class UserServiceTest {

    @Mock
    private UserDAO userDAO;

    @Mock
    private PasswordService passwordService;

    @Mock
    private SessionService sessionService;

    @Mock
    private IdGenerator idGenerator;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userDAO, passwordService, sessionService, idGenerator);
    }

    @Nested
    @DisplayName("用户注册测试")
    class RegisterUserTests {

        @Test
        @DisplayName("应该成功注册新用户")
        void shouldRegisterUserSuccessfully() {
            // Given
            String username = "testuser";
            String email = "test@example.com";
            String password = "Test@123456";
            String fullName = "Test User";
            String userId = UUID.randomUUID().toString();
            String passwordHash = "$2a$10$abcdef...";

            when(userDAO.existsByUsername(username)).thenReturn(false);
            when(userDAO.existsByEmail(email)).thenReturn(false);
            when(passwordService.isPasswordStrong(password)).thenReturn(true);
            when(passwordService.hashPassword(password)).thenReturn(passwordHash);
            when(idGenerator.generateId()).thenReturn(UUID.fromString(userId));

            // When
            User result = userService.registerUser(username, email, password, fullName);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(userId);
            assertThat(result.username()).isEqualTo(username);
            assertThat(result.email()).isEqualTo(email);
            assertThat(result.fullName()).isEqualTo(fullName);
            assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(result.systemAdmin()).isFalse();
            assertThat(result.emailVerified()).isFalse();

            // Verify interactions
            verify(userDAO).existsByUsername(username);
            verify(userDAO).existsByEmail(email);
            verify(passwordService).isPasswordStrong(password);
            verify(passwordService).hashPassword(password);
            verify(userDAO).insert(any(User.class));
        }

        @Test
        @DisplayName("应该拒绝重复的用户名")
        void shouldRejectDuplicateUsername() {
            // Given
            String username = "existinguser";
            when(userDAO.existsByUsername(username)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> userService.registerUser(username, "test@example.com", "Test@123", null))
                    .isInstanceOf(ConflictException.class);

            verify(userDAO).existsByUsername(username);
            verify(userDAO, never()).insert(any());
        }

        @Test
        @DisplayName("应该拒绝重复的邮箱")
        void shouldRejectDuplicateEmail() {
            // Given
            String email = "existing@example.com";
            when(userDAO.existsByUsername(anyString())).thenReturn(false);
            when(userDAO.existsByEmail(email)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> userService.registerUser("testuser", email, "Test@123", null))
                    .isInstanceOf(ConflictException.class);

            verify(userDAO).existsByEmail(email);
            verify(userDAO, never()).insert(any());
        }

        @Test
        @DisplayName("应该拒绝弱密码")
        void shouldRejectWeakPassword() {
            // Given
            String weakPassword = "123456";
            when(userDAO.existsByUsername(anyString())).thenReturn(false);
            when(userDAO.existsByEmail(anyString())).thenReturn(false);
            when(passwordService.isPasswordStrong(weakPassword)).thenReturn(false);

            // When & Then
            assertThatThrownBy(
                    () -> userService.registerUser("testuser", "test@example.com", weakPassword, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Password must be at least 8 characters");

            verify(passwordService).isPasswordStrong(weakPassword);
            verify(userDAO, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("用户登录测试")
    class LoginTests {

        @Test
        @DisplayName("应该成功登录并创建Session")
        void shouldLoginSuccessfully() {
            // Given
            String username = "testuser";
            String password = "Test@123456";
            String ipAddress = "192.168.1.100";
            String userAgent = "Mozilla/5.0";
            String userId = UUID.randomUUID().toString();
            String passwordHash = "$2a$10$abcdef...";
            String sessionToken = "session_token_abc123";

            User user = User.builder()
                    .id(userId)
                    .username(username)
                    .email("test@example.com")
                    .passwordHash(passwordHash)
                    .status(UserStatus.ACTIVE)
                    .build();

            Session session = Session.builder()
                    .sessionToken(sessionToken)
                    .userId(userId)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            when(userDAO.findByUsername(username)).thenReturn(Optional.of(user));
            when(passwordService.verifyPassword(password, passwordHash)).thenReturn(true);
            when(sessionService.createSession(eq(userId), eq(ipAddress), eq(userAgent))).thenReturn(session);

            // When
            UserService.LoginResult result = userService.login(username, password, ipAddress, userAgent);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.sessionToken()).isEqualTo(sessionToken);
            assertThat(result.user()).isEqualTo(user);
            assertThat(result.expiresAt()).isEqualTo(session.expiresAt());

            verify(userDAO).findByUsername(username);
            verify(passwordService).verifyPassword(password, passwordHash);
            verify(sessionService).createSession(userId, ipAddress, userAgent);
            verify(userDAO).updateLastLogin(eq(userId), any(Instant.class));
        }

        @Test
        @DisplayName("应该拒绝不存在的用户名")
        void shouldRejectNonExistentUsername() {
            // Given
            String username = "nonexistent";
            when(userDAO.findByUsername(username)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.login(username, "password", "127.0.0.1", "agent"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid username or password");

            verify(userDAO).findByUsername(username);
            verify(passwordService, never()).verifyPassword(anyString(), anyString());
            verify(sessionService, never()).createSession(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("应该拒绝错误的密码")
        void shouldRejectInvalidPassword() {
            // Given
            String username = "testuser";
            String password = "wrongpassword";
            String passwordHash = "$2a$10$abcdef...";

            User user = User.builder()
                    .id(UUID.randomUUID().toString())
                    .username(username)
                    .passwordHash(passwordHash)
                    .status(UserStatus.ACTIVE)
                    .build();

            when(userDAO.findByUsername(username)).thenReturn(Optional.of(user));
            when(passwordService.verifyPassword(password, passwordHash)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> userService.login(username, password, "127.0.0.1", "agent"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid username or password");

            verify(passwordService).verifyPassword(password, passwordHash);
            verify(sessionService, never()).createSession(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("应该拒绝非活跃状态的用户")
        void shouldRejectSuspendedUser() {
            // Given
            String username = "suspended";
            String password = "Test@123456";
            String passwordHash = "$2a$10$abcdef...";

            User user = User.builder()
                    .id(UUID.randomUUID().toString())
                    .username(username)
                    .passwordHash(passwordHash)
                    .status(UserStatus.SUSPENDED)
                    .build();

            when(userDAO.findByUsername(username)).thenReturn(Optional.of(user));
            when(passwordService.verifyPassword(password, passwordHash)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> userService.login(username, password, "127.0.0.1", "agent"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("User account is suspended or deleted");

            verify(sessionService, never()).createSession(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("用户更新测试")
    class UpdateUserTests {

        @Test
        @DisplayName("应该成功更新用户资料")
        void shouldUpdateUserProfileSuccessfully() {
            // Given
            String userId = UUID.randomUUID().toString();
            String newEmail = "newemail@example.com";
            String newFullName = "New Name";
            String updatedBy = "admin";

            User existingUser = User.builder()
                    .id(userId)
                    .username("testuser")
                    .email("old@example.com")
                    .fullName("Old Name")
                    .status(UserStatus.ACTIVE)
                    .build();

            User updatedUser = User.builder()
                    .id(userId)
                    .username("testuser")
                    .email(newEmail)
                    .fullName(newFullName)
                    .status(UserStatus.ACTIVE)
                    .build();

            when(userDAO.findById(userId)).thenReturn(Optional.of(existingUser))
                    .thenReturn(Optional.of(updatedUser));
            when(userDAO.existsByEmail(newEmail)).thenReturn(false);

            // When
            User result = userService.updateUser(userId, newEmail, newFullName, null, null, updatedBy);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.email()).isEqualTo(newEmail);
            assertThat(result.fullName()).isEqualTo(newFullName);

            verify(userDAO).update(eq(userId), eq(newEmail), eq(newFullName), any(), any(), eq(updatedBy));
        }

        @Test
        @DisplayName("应该拒绝更新为已存在的邮箱")
        void shouldRejectDuplicateEmailOnUpdate() {
            // Given
            String userId = UUID.randomUUID().toString();
            String existingEmail = "existing@example.com";

            User user = User.builder()
                    .id(userId)
                    .username("testuser")
                    .email("old@example.com")
                    .build();

            when(userDAO.findById(userId)).thenReturn(Optional.of(user));
            when(userDAO.existsByEmail(existingEmail)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> userService.updateUser(userId, existingEmail, null, null, null, "admin"))
                    .isInstanceOf(ConflictException.class);

            verify(userDAO, never()).update(anyString(), anyString(), anyString(), anyString(), anyString(),
                    anyString());
        }

        @Test
        @DisplayName("应该在用户不存在时抛出异常")
        void shouldThrowExceptionWhenUserNotFound() {
            // Given
            String userId = UUID.randomUUID().toString();
            when(userDAO.findById(userId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.updateUser(userId, null, null, null, null, "admin"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("密码修改测试")
    class ChangePasswordTests {

        @Test
        @DisplayName("应该成功修改密码")
        void shouldChangePasswordSuccessfully() {
            // Given
            String userId = UUID.randomUUID().toString();
            String oldPassword = "OldPassword@123";
            String newPassword = "NewPassword@456";
            String oldPasswordHash = "$2a$10$old...";
            String newPasswordHash = "$2a$10$new...";
            String updatedBy = "user";

            User user = User.builder()
                    .id(userId)
                    .username("testuser")
                    .passwordHash(oldPasswordHash)
                    .build();

            when(userDAO.findById(userId)).thenReturn(Optional.of(user));
            when(passwordService.verifyPassword(oldPassword, oldPasswordHash)).thenReturn(true);
            when(passwordService.isPasswordStrong(newPassword)).thenReturn(true);
            when(passwordService.hashPassword(newPassword)).thenReturn(newPasswordHash);

            // When
            userService.changePassword(userId, oldPassword, newPassword, updatedBy);

            // Then
            verify(passwordService).verifyPassword(oldPassword, oldPasswordHash);
            verify(passwordService).isPasswordStrong(newPassword);
            verify(passwordService).hashPassword(newPassword);
            verify(userDAO).updatePassword(userId, newPasswordHash, updatedBy);
        }

        @Test
        @DisplayName("应该拒绝错误的旧密码")
        void shouldRejectInvalidOldPassword() {
            // Given
            String userId = UUID.randomUUID().toString();
            String oldPassword = "WrongPassword";
            String passwordHash = "$2a$10$old...";

            User user = User.builder()
                    .id(userId)
                    .passwordHash(passwordHash)
                    .build();

            when(userDAO.findById(userId)).thenReturn(Optional.of(user));
            when(passwordService.verifyPassword(oldPassword, passwordHash)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> userService.changePassword(userId, oldPassword, "NewPassword@456", "user"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid old password");

            verify(userDAO, never()).updatePassword(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("应该拒绝弱的新密码")
        void shouldRejectWeakNewPassword() {
            // Given
            String userId = UUID.randomUUID().toString();
            String oldPassword = "OldPassword@123";
            String newPassword = "weak";
            String passwordHash = "$2a$10$old...";

            User user = User.builder()
                    .id(userId)
                    .passwordHash(passwordHash)
                    .build();

            when(userDAO.findById(userId)).thenReturn(Optional.of(user));
            when(passwordService.verifyPassword(oldPassword, passwordHash)).thenReturn(true);
            when(passwordService.isPasswordStrong(newPassword)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> userService.changePassword(userId, oldPassword, newPassword, "user"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("New password must be at least 8 characters");

            verify(userDAO, never()).updatePassword(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("用户删除测试")
    class DeleteUserTests {

        @Test
        @DisplayName("应该成功软删除用户")
        void shouldSoftDeleteUserSuccessfully() {
            // Given
            String userId = UUID.randomUUID().toString();
            String deletedBy = "admin";

            User user = User.builder()
                    .id(userId)
                    .username("testuser")
                    .status(UserStatus.ACTIVE)
                    .build();

            when(userDAO.findById(userId)).thenReturn(Optional.of(user));

            // When
            userService.deleteUser(userId, deletedBy);

            // Then
            verify(userDAO).updateStatus(userId, UserStatus.DELETED, deletedBy);
        }

        @Test
        @DisplayName("应该在用户不存在时抛出异常")
        void shouldThrowExceptionWhenUserNotFoundOnDelete() {
            // Given
            String userId = UUID.randomUUID().toString();
            when(userDAO.findById(userId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.deleteUser(userId, "admin"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User not found");
        }
    }
}
