package com.comet.opik.domain;

import com.comet.opik.api.User;
import com.comet.opik.api.UserStatus;
import com.comet.opik.api.error.ConflictException;
import com.comet.opik.infrastructure.audit.Auditable;
import com.comet.opik.infrastructure.audit.Operation;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class UserService {

    private final @NonNull UserDAO userDAO;
    private final @NonNull PasswordService passwordService;
    private final @NonNull SessionService sessionService;
    private final @NonNull IdGenerator idGenerator;

    /**
     * Register a new user
     *
     * @param username the username
     * @param email the email address
     * @param password the password
     * @param fullName the full name (optional)
     * @return the created user
     */
    @Auditable(action = "Register User", resourceType = "user", operation = Operation.CREATE)
    public User registerUser(String username, String email, String password, String fullName) {
        log.info("Registering new user: '{}'", username);

        // Validate username uniqueness
        if (userDAO.existsByUsername(username)) {
            throw new ConflictException("Username already exists: '%s'".formatted(username));
        }

        // Validate email uniqueness
        if (userDAO.existsByEmail(email)) {
            throw new ConflictException("Email already exists: '%s'".formatted(email));
        }

        // Validate password strength
        if (!passwordService.isPasswordStrong(password)) {
            throw new BadRequestException(
                    "Password must be at least 8 characters and contain uppercase, lowercase, digit and special character");
        }

        // Hash password
        String passwordHash = passwordService.hashPassword(password);

        Instant now = Instant.now();

        // Create user
        var user = User.builder()
                .id(idGenerator.generateId().toString())
                .username(username)
                .email(email)
                .passwordHash(passwordHash)
                .fullName(fullName)
                .status(UserStatus.ACTIVE)
                .systemAdmin(false)
                .emailVerified(false)
                .locale("en-US")
                .version(0)
                .createdAt(now)
                .createdBy("system")
                .lastUpdatedAt(now)
                .lastUpdatedBy("system")
                .build();

        userDAO.insert(user);

        log.info("User registered successfully: '{}'", username);

        return user;
    }

    /**
     * Authenticate a user and create a session
     *
     * @param username the username
     * @param password the password
     * @param ipAddress the IP address
     * @param userAgent the user agent
     * @return login result with session token and user info
     */
    @Auditable(action = "User Login", resourceType = "user", operation = Operation.LOGIN)
    public LoginResult login(String username, String password, String ipAddress, String userAgent) {
        log.info("User login attempt: '{}'", username);

        // Find user
        var user = userDAO.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Login failed: user not found '{}'", username);
                    return new BadRequestException("Invalid username or password");
                });

        // Verify password
        if (!passwordService.verifyPassword(password, user.passwordHash())) {
            log.warn("Login failed: invalid password for user '{}'", username);
            throw new BadRequestException("Invalid username or password");
        }

        // Check user status
        if (user.status() != UserStatus.ACTIVE) {
            log.warn("Login failed: user '{}' is not active: '{}'", username, user.status());
            throw new BadRequestException("User account is suspended or deleted");
        }

        // Create session
        var session = sessionService.createSession(user.id(), ipAddress, userAgent);

        // Update last login time
        userDAO.updateLastLogin(user.id(), Instant.now());

        log.info("User '{}' logged in successfully", username);

        return new LoginResult(session.sessionToken(), user, session.expiresAt());
    }

    /**
     * Logout a user
     *
     * @param sessionToken the session token
     */
    @Auditable(action = "User Logout", resourceType = "user", operation = Operation.LOGOUT)
    public void logout(String sessionToken) {
        log.info("User logout");
        sessionService.invalidateSession(sessionToken);
        log.info("User logged out successfully");
    }

    /**
     * Get user by ID
     *
     * @param userId the user ID
     * @return the user
     */
    public Optional<User> getUser(String userId) {
        return userDAO.findById(userId);
    }

    /**
     * Get user by username
     *
     * @param username the username
     * @return the user
     */
    public Optional<User> getUserByUsername(String username) {
        return userDAO.findByUsername(username);
    }

    /**
     * Get user by email
     *
     * @param email the email
     * @return the user
     */
    public Optional<User> getUserByEmail(String email) {
        return userDAO.findByEmail(email);
    }

    /**
     * Update user profile
     *
     * @param userId the user ID
     * @param email the new email (optional)
     * @param fullName the new full name (optional)
     * @param avatarUrl the new avatar URL (optional)
     * @param locale the new locale (optional)
     * @param updatedBy the user who made the update
     * @return the updated user
     */
    @Auditable(action = "Update User Profile", resourceType = "user", operation = Operation.UPDATE)
    public User updateUser(String userId, String email, String fullName, String avatarUrl, String locale,
            String updatedBy) {
        log.info("Updating user: '{}'", userId);

        var user = getUser(userId)
                .orElseThrow(() -> new NotFoundException("User not found: '%s'".formatted(userId)));

        // Check email uniqueness if changed
        if (email != null && !email.equals(user.email())) {
            if (userDAO.existsByEmail(email)) {
                throw new ConflictException("Email already in use: '%s'".formatted(email));
            }
        }

        userDAO.update(userId,
                email != null ? email : user.email(),
                fullName != null ? fullName : user.fullName(),
                avatarUrl != null ? avatarUrl : user.avatarUrl(),
                locale != null ? locale : user.locale(),
                updatedBy);

        log.info("User '{}' updated successfully", userId);

        return getUser(userId).orElseThrow();
    }

    /**
     * Change user password
     *
     * @param userId the user ID
     * @param oldPassword the old password
     * @param newPassword the new password
     * @param updatedBy the user who made the change
     */
    @Auditable(action = "Change Password", resourceType = "user", operation = Operation.UPDATE)
    public void changePassword(String userId, String oldPassword, String newPassword, String updatedBy) {
        log.info("Changing password for user: '{}'", userId);

        var user = getUser(userId)
                .orElseThrow(() -> new NotFoundException("User not found: '%s'".formatted(userId)));

        // Verify old password
        if (!passwordService.verifyPassword(oldPassword, user.passwordHash())) {
            throw new BadRequestException("Invalid old password");
        }

        // Validate new password strength
        if (!passwordService.isPasswordStrong(newPassword)) {
            throw new BadRequestException(
                    "New password must be at least 8 characters and contain uppercase, lowercase, digit and special character");
        }

        // Hash new password
        String newPasswordHash = passwordService.hashPassword(newPassword);

        userDAO.updatePassword(userId, newPasswordHash, updatedBy);

        log.info("Password changed successfully for user: '{}'", userId);
    }

    /**
     * Update user status
     *
     * @param userId the user ID
     * @param status the new status
     * @param updatedBy the user who made the update
     */
    public void updateUserStatus(String userId, UserStatus status, String updatedBy) {
        log.info("Updating user status: '{}' to '{}'", userId, status);

        var user = getUser(userId)
                .orElseThrow(() -> new NotFoundException("User not found: '%s'".formatted(userId)));

        userDAO.updateStatus(userId, status, updatedBy);

        log.info("User '{}' status updated to '{}'", user.username(), status);
    }

    /**
     * Delete user (soft delete by setting status to DELETED)
     *
     * @param userId the user ID
     * @param deletedBy the user who made the deletion
     */
    @Auditable(action = "Delete User", resourceType = "user", operation = Operation.DELETE)
    public void deleteUser(String userId, String deletedBy) {
        log.info("Deleting user: '{}'", userId);

        updateUserStatus(userId, UserStatus.DELETED, deletedBy);

        log.info("User '{}' deleted successfully", userId);
    }

    /**
     * Login result containing session token and user info
     */
    public record LoginResult(String sessionToken, User user, Instant expiresAt) {
    }
}
