package com.comet.opik.domain;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

import java.util.regex.Pattern;

@Slf4j
@Singleton
public class PasswordService {

    private static final int BCRYPT_COST = 12;
    private static final int MIN_PASSWORD_LENGTH = 8;

    // Password must contain: uppercase, lowercase, digit, special character
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(".*[@$!%*?&#^()_+=\\-\\[\\]{}|;:,.<>].*");

    /**
     * Hash a password using BCrypt
     *
     * @param plainPassword the plain text password
     * @return the hashed password
     */
    public String hashPassword(String plainPassword) {
        log.debug("Hashing password");
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_COST));
    }

    /**
     * Verify a password against a hash
     *
     * @param plainPassword the plain text password
     * @param hashedPassword the hashed password to compare against
     * @return true if the password matches, false otherwise
     */
    public boolean verifyPassword(String plainPassword, String hashedPassword) {
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (Exception e) {
            log.error("Password verification failed", e);
            return false;
        }
    }

    /**
     * Check if a password meets strength requirements
     * Requirements:
     * - At least 8 characters
     * - Contains uppercase letter
     * - Contains lowercase letter
     * - Contains digit
     * - Contains special character
     *
     * @param password the password to check
     * @return true if the password is strong enough, false otherwise
     */
    public boolean isPasswordStrong(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            log.debug("Password too short: length='{}'", password != null ? password.length() : 0);
            return false;
        }

        boolean hasUppercase = UPPERCASE_PATTERN.matcher(password).matches();
        boolean hasLowercase = LOWERCASE_PATTERN.matcher(password).matches();
        boolean hasDigit = DIGIT_PATTERN.matcher(password).matches();
        boolean hasSpecial = SPECIAL_CHAR_PATTERN.matcher(password).matches();

        boolean isStrong = hasUppercase && hasLowercase && hasDigit && hasSpecial;

        if (!isStrong) {
            log.debug(
                    "Password does not meet strength requirements: uppercase='{}', lowercase='{}', digit='{}', special='{}'",
                    hasUppercase, hasLowercase, hasDigit, hasSpecial);
        }

        return isStrong;
    }

    /**
     * Generate a password strength score (0-4)
     *
     * @param password the password to score
     * @return strength score: 0=very weak, 1=weak, 2=fair, 3=good, 4=strong
     */
    public int getPasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }

        int score = 0;

        if (password.length() >= MIN_PASSWORD_LENGTH) {
            score++;
        }
        if (UPPERCASE_PATTERN.matcher(password).matches()) {
            score++;
        }
        if (LOWERCASE_PATTERN.matcher(password).matches()) {
            score++;
        }
        if (DIGIT_PATTERN.matcher(password).matches()) {
            score++;
        }
        if (SPECIAL_CHAR_PATTERN.matcher(password).matches()) {
            score++;
        }

        // Adjust score based on length
        if (password.length() >= 12) {
            score = Math.min(score + 1, 5);
        }

        return Math.min(score, 4);
    }
}
