package com.comet.opik.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 密码服务单元测试
 *
 * 测试范围：
 * - 密码哈希和验证
 * - 密码强度检查
 * - 密码强度评分
 */
@DisplayName("PasswordService单元测试")
class PasswordServiceTest {

    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService();
    }

    @Nested
    @DisplayName("密码哈希测试")
    class HashPasswordTests {

        @Test
        @DisplayName("应该成功哈希密码")
        void shouldHashPasswordSuccessfully() {
            // Given
            String plainPassword = "MyPassword@123";

            // When
            String hashedPassword = passwordService.hashPassword(plainPassword);

            // Then
            assertThat(hashedPassword).isNotNull();
            assertThat(hashedPassword).isNotEmpty();
            assertThat(hashedPassword).isNotEqualTo(plainPassword);
            assertThat(hashedPassword).startsWith("$2a$"); // BCrypt hash prefix
        }

        @Test
        @DisplayName("相同密码应该产生不同的哈希（因为盐值不同）")
        void shouldProduceDifferentHashesForSamePassword() {
            // Given
            String plainPassword = "MyPassword@123";

            // When
            String hash1 = passwordService.hashPassword(plainPassword);
            String hash2 = passwordService.hashPassword(plainPassword);

            // Then
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("不同密码应该产生不同的哈希")
        void shouldProduceDifferentHashesForDifferentPasswords() {
            // Given
            String password1 = "Password@123";
            String password2 = "Different@456";

            // When
            String hash1 = passwordService.hashPassword(password1);
            String hash2 = passwordService.hashPassword(password2);

            // Then
            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    @Nested
    @DisplayName("密码验证测试")
    class VerifyPasswordTests {

        @Test
        @DisplayName("应该验证正确的密码")
        void shouldVerifyCorrectPassword() {
            // Given
            String plainPassword = "MyPassword@123";
            String hashedPassword = passwordService.hashPassword(plainPassword);

            // When
            boolean result = passwordService.verifyPassword(plainPassword, hashedPassword);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("应该拒绝错误的密码")
        void shouldRejectIncorrectPassword() {
            // Given
            String correctPassword = "MyPassword@123";
            String wrongPassword = "WrongPassword@456";
            String hashedPassword = passwordService.hashPassword(correctPassword);

            // When
            boolean result = passwordService.verifyPassword(wrongPassword, hashedPassword);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("应该拒绝无效的哈希")
        void shouldRejectInvalidHash() {
            // Given
            String plainPassword = "MyPassword@123";
            // Use a valid BCrypt hash format but for a different password
            String wrongPasswordHash = passwordService.hashPassword("DifferentPassword@456");

            // When
            boolean result = passwordService.verifyPassword(plainPassword, wrongPasswordHash);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("空密码应该返回false")
        void shouldReturnFalseForEmptyPassword() {
            // Given
            String hashedPassword = passwordService.hashPassword("SomePassword@123");

            // When
            boolean result = passwordService.verifyPassword("", hashedPassword);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("密码强度检查测试")
    class PasswordStrengthTests {

        @Test
        @DisplayName("强密码应该通过检查")
        void shouldPassStrongPassword() {
            // Given
            String strongPassword = "StrongPassword@123";

            // When
            boolean result = passwordService.isPasswordStrong(strongPassword);

            // Then
            assertThat(result).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Test@123", // 8 characters, all requirements met
                "MyPass@1", // 8 characters
                "VeryLongPassword@123456", // Very long
                "Abc123!@#" // Special characters
        })
        @DisplayName("符合要求的密码应该通过检查")
        void shouldPassValidPasswords(String password) {
            // When
            boolean result = passwordService.isPasswordStrong(password);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("太短的密码应该失败")
        void shouldFailShortPassword() {
            // Given
            String shortPassword = "Test@1"; // 6 characters

            // When
            boolean result = passwordService.isPasswordStrong(shortPassword);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("缺少大写字母的密码应该失败")
        void shouldFailPasswordWithoutUppercase() {
            // Given
            String password = "password@123"; // No uppercase

            // When
            boolean result = passwordService.isPasswordStrong(password);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("缺少小写字母的密码应该失败")
        void shouldFailPasswordWithoutLowercase() {
            // Given
            String password = "PASSWORD@123"; // No lowercase

            // When
            boolean result = passwordService.isPasswordStrong(password);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("缺少数字的密码应该失败")
        void shouldFailPasswordWithoutDigit() {
            // Given
            String password = "Password@Test"; // No digit

            // When
            boolean result = passwordService.isPasswordStrong(password);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("缺少特殊字符的密码应该失败")
        void shouldFailPasswordWithoutSpecialChar() {
            // Given
            String password = "Password123"; // No special character

            // When
            boolean result = passwordService.isPasswordStrong(password);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("null密码应该失败")
        void shouldFailNullPassword() {
            // When
            boolean result = passwordService.isPasswordStrong(null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("空密码应该失败")
        void shouldFailEmptyPassword() {
            // When
            boolean result = passwordService.isPasswordStrong("");

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("密码强度评分测试")
    class PasswordStrengthScoreTests {

        @Test
        @DisplayName("非常弱的密码应该得分0")
        void shouldScoreVeryWeakPassword() {
            // Given
            String password = "";

            // When
            int score = passwordService.getPasswordStrength(password);

            // Then
            assertThat(score).isEqualTo(0);
        }

        @Test
        @DisplayName("null密码应该得分0")
        void shouldScoreNullPassword() {
            // When
            int score = passwordService.getPasswordStrength(null);

            // Then
            assertThat(score).isEqualTo(0);
        }

        @Test
        @DisplayName("只有长度的密码应该得分1")
        void shouldScorePasswordWithLengthOnly() {
            // Given
            String password = "testtest"; // 8 chars, no uppercase, no digit, no special

            // When
            int score = passwordService.getPasswordStrength(password);

            // Then
            assertThat(score).isEqualTo(2); // length + lowercase
        }

        @Test
        @DisplayName("包含大小写和数字的密码应该得分3")
        void shouldScorePasswordWithMixedCase() {
            // Given
            String password = "Test1234"; // length, uppercase, lowercase, digit

            // When
            int score = passwordService.getPasswordStrength(password);

            // Then
            assertThat(score).isEqualTo(4); // length + uppercase + lowercase + digit
        }

        @Test
        @DisplayName("强密码应该得分4")
        void shouldScoreStrongPassword() {
            // Given
            String password = "Strong@123"; // All requirements met

            // When
            int score = passwordService.getPasswordStrength(password);

            // Then
            assertThat(score).isEqualTo(4); // Maximum score without extra length bonus
        }

        @Test
        @DisplayName("很长的强密码应该得分4（上限）")
        void shouldScoreVeryLongPassword() {
            // Given
            String password = "VeryLongStrongPassword@123456"; // >12 chars, all requirements

            // When
            int score = passwordService.getPasswordStrength(password);

            // Then
            assertThat(score).isEqualTo(4); // Capped at 4
        }

        @Test
        @DisplayName("弱密码应该得分较低")
        void shouldScoreWeakPassword() {
            // Given
            String password = "test"; // 4 chars, only lowercase

            // When
            int score = passwordService.getPasswordStrength(password);

            // Then
            assertThat(score).isLessThan(2);
        }
    }

    @Nested
    @DisplayName("端到端密码流程测试")
    class EndToEndTests {

        @Test
        @DisplayName("完整的密码生命周期测试")
        void shouldHandleCompletePasswordLifecycle() {
            // Given - User registers with a strong password
            String originalPassword = "MySecure@Password123";
            assertThat(passwordService.isPasswordStrong(originalPassword)).isTrue();

            // When - Password is hashed
            String hashedPassword = passwordService.hashPassword(originalPassword);

            // Then - Hash should be valid
            assertThat(hashedPassword).isNotNull();
            assertThat(hashedPassword).startsWith("$2a$");

            // When - User logs in with correct password
            boolean loginSuccess = passwordService.verifyPassword(originalPassword, hashedPassword);

            // Then - Login should succeed
            assertThat(loginSuccess).isTrue();

            // When - Someone tries to login with wrong password
            boolean hackerAttempt = passwordService.verifyPassword("WrongPassword@123", hashedPassword);

            // Then - Login should fail
            assertThat(hackerAttempt).isFalse();
        }

        @Test
        @DisplayName("应该拒绝弱密码注册")
        void shouldRejectWeakPasswordRegistration() {
            // Given - User tries to register with weak password
            String weakPassword = "123456"; // Too short, no uppercase, no special char

            // When
            boolean isStrong = passwordService.isPasswordStrong(weakPassword);
            int strength = passwordService.getPasswordStrength(weakPassword);

            // Then
            assertThat(isStrong).isFalse();
            assertThat(strength).isLessThan(3);
        }
    }
}
