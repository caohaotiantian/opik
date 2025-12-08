package com.comet.opik.domain;

import com.comet.opik.api.PasswordResetStatus;
import com.comet.opik.api.PasswordResetToken;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.guicey.jdbi3.tx.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static com.comet.opik.infrastructure.db.TransactionTemplateAsync.WRITE;

/**
 * Password reset service
 *
 * Handles password reset token generation, validation and password update
 *
 * Note: This is a simplified implementation without email sending.
 * In production, integrate with email service to send reset links.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PasswordResetService {

    private final @NonNull PasswordResetTokenDAO tokenDAO;
    private final @NonNull UserDAO userDAO;
    private final @NonNull PasswordService passwordService;
    private final @NonNull IdGenerator idGenerator;
    private final @NonNull TransactionTemplate transactionTemplate;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_LENGTH = 32;
    private static final int TOKEN_EXPIRY_HOURS = 24;

    /**
     * Request a password reset
     *
     * Generates a reset token for the user. In production, this would
     * send an email with the reset link.
     *
     * @param email the user's email
     * @param ipAddress the IP address of the request
     * @return the generated reset token (for testing only - in production, don't return this)
     */
    public String requestPasswordReset(String email, String ipAddress) {
        log.info("Password reset requested for email (hashed for privacy)");

        // Find user by email
        var user = userDAO.findByEmail(email)
                .orElseThrow(() -> {
                    // Don't reveal whether user exists - log but return same error
                    log.warn("Password reset requested for non-existent email");
                    // Return generic message to prevent email enumeration
                    return new BadRequestException("If an account exists with this email, a reset link has been sent");
                });

        // Invalidate any existing pending tokens for this user
        tokenDAO.invalidateAllUserTokens(user.id());

        // Generate new token
        String resetToken = generateSecureToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(TOKEN_EXPIRY_HOURS, ChronoUnit.HOURS);

        var passwordResetToken = PasswordResetToken.builder()
                .id(idGenerator.generateId().toString())
                .userId(user.id())
                .token(resetToken)
                .status(PasswordResetStatus.PENDING)
                .ipAddress(ipAddress)
                .expiresAt(expiresAt)
                .createdAt(now)
                .createdBy("system")
                .lastUpdatedAt(now)
                .lastUpdatedBy("system")
                .build();

        transactionTemplate.inTransaction(WRITE, handle -> {
            handle.attach(PasswordResetTokenDAO.class).insert(passwordResetToken);
            return null;
        });

        log.info("Password reset token generated for user: '{}', expires at: '{}'", user.id(), expiresAt);

        // In production: Send email with reset link here
        // For now, return token for testing purposes
        return resetToken;
    }

    /**
     * Reset password using token
     *
     * @param token the reset token
     * @param newPassword the new password
     */
    public void resetPassword(String token, String newPassword) {
        log.info("Password reset attempt with token");

        // Validate token
        var resetToken = tokenDAO.findValidToken(token, Instant.now())
                .orElseThrow(() -> {
                    log.warn("Invalid or expired password reset token");
                    return new BadRequestException("Invalid or expired reset token");
                });

        // Validate password strength
        if (!passwordService.isPasswordStrong(newPassword)) {
            throw new BadRequestException(
                    "Password must be at least 8 characters and contain uppercase, lowercase, digit and special character");
        }

        // Get user
        var user = userDAO.findById(resetToken.userId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Update password and mark token as used
        transactionTemplate.inTransaction(WRITE, handle -> {
            var uDao = handle.attach(UserDAO.class);
            var tDao = handle.attach(PasswordResetTokenDAO.class);

            // Update password
            String newPasswordHash = passwordService.hashPassword(newPassword);
            uDao.updatePassword(user.id(), newPasswordHash, "system");

            // Mark token as used
            tDao.updateStatus(resetToken.id(), PasswordResetStatus.USED, Instant.now(), "system");

            return null;
        });

        log.info("Password reset successful for user: '{}'", user.id());
    }

    /**
     * Validate a reset token without using it
     *
     * @param token the reset token
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String token) {
        return tokenDAO.findValidToken(token, Instant.now()).isPresent();
    }

    /**
     * Clean up expired tokens
     *
     * @return number of tokens deleted
     */
    public int cleanupExpiredTokens() {
        log.debug("Cleaning up expired password reset tokens");

        int deleted = tokenDAO.deleteExpired(Instant.now());

        if (deleted > 0) {
            log.info("Cleaned up '{}' expired password reset tokens", deleted);
        }

        return deleted;
    }

    /**
     * Generate a secure random token
     */
    private String generateSecureToken() {
        byte[] randomBytes = new byte[TOKEN_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
