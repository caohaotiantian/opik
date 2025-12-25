package com.comet.opik.domain;

import com.comet.opik.api.ApiKey;
import com.comet.opik.api.ApiKeyPage;
import com.comet.opik.api.ApiKeyStatus;
import com.comet.opik.infrastructure.audit.Auditable;
import com.comet.opik.infrastructure.audit.Operation;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import ru.vyarus.guicey.jdbi3.tx.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.comet.opik.infrastructure.db.TransactionTemplateAsync.READ_ONLY;
import static com.comet.opik.infrastructure.db.TransactionTemplateAsync.WRITE;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ApiKeyService {

    private final @NonNull ApiKeyDAO apiKeyDAO;
    private final @NonNull IdGenerator idGenerator;
    private final @NonNull TransactionTemplate transactionTemplate;

    private static final String API_KEY_PREFIX = "opik_";
    private static final int API_KEY_LENGTH = 48;
    private static final int MAX_API_KEYS_PER_USER = 50;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generate a new API key
     *
     * @param userId the user ID
     * @param workspaceId the workspace ID
     * @param name the API key name
     * @param description the API key description
     * @param scopes the permission scopes (optional)
     * @param expiryDays the expiry days (optional, null means no expiry)
     * @return the generated API key (plain text, only returned once)
     */
    @Auditable(action = "Generate API Key", resourceType = "api_key", operation = Operation.CREATE)
    public ApiKeyResult generateApiKey(String userId, String workspaceId, String name, String description,
            Set<String> scopes, Integer expiryDays) {
        log.info("Generating API key for user: '{}' in workspace: '{}'", userId, workspaceId);

        // Generate API key
        String apiKey = generateSecureApiKey();
        String keyHash = hashApiKey(apiKey);
        String keyPrefix = apiKey.substring(0, Math.min(10, apiKey.length())); // Max 10 chars to match DB column

        // Calculate expiry
        Instant expiresAt = expiryDays != null ? Instant.now().plus(expiryDays, ChronoUnit.DAYS) : null;

        Instant now = Instant.now();

        // Create API key entity
        var apiKeyEntity = ApiKey.builder()
                .id(idGenerator.generateId().toString())
                .userId(userId)
                .workspaceId(workspaceId)
                .name(name)
                .description(description)
                .keyHash(keyHash)
                .keyPrefix(keyPrefix)
                .status(ApiKeyStatus.ACTIVE)
                .scopes(scopes)
                .expiresAt(expiresAt)
                .version(0)
                .createdAt(now)
                .createdBy(userId)
                .lastUpdatedAt(now)
                .lastUpdatedBy(userId)
                .build();

        // Save to database within transaction
        transactionTemplate.inTransaction(WRITE, handle -> {
            var dao = handle.attach(ApiKeyDAO.class);

            // Check user's API key count
            int userKeyCount = dao.countActiveByUser(userId);
            if (userKeyCount >= MAX_API_KEYS_PER_USER) {
                throw new BadRequestException(
                        "Maximum API keys limit reached. Maximum allowed: " + MAX_API_KEYS_PER_USER);
            }

            dao.insert(apiKeyEntity);
            return null;
        });

        log.info("API key generated successfully: '{}' for user: '{}'", apiKeyEntity.id(), userId);

        // Return API key with plain text (only time it's exposed)
        return new ApiKeyResult(apiKey, apiKeyEntity); // plainApiKey, apiKey
    }

    /**
     * Validate an API key
     *
     * @param apiKey the API key to validate
     * @return the API key info if valid
     */
    public Optional<ApiKey> validateApiKey(String apiKey) {
        log.debug("Validating API key");

        String keyHash = hashApiKey(apiKey);
        var apiKeyInfo = transactionTemplate.inTransaction(READ_ONLY, handle -> {
            return handle.attach(ApiKeyDAO.class).findByKeyHash(keyHash);
        });

        if (apiKeyInfo.isEmpty()) {
            log.debug("API key not found");
            return Optional.empty();
        }

        // Check status
        if (apiKeyInfo.get().status() != ApiKeyStatus.ACTIVE) {
            log.warn("API key is not active: '{}'", apiKeyInfo.get().status());
            return Optional.empty();
        }

        // Check expiry
        if (apiKeyInfo.get().expiresAt() != null && apiKeyInfo.get().expiresAt().isBefore(Instant.now())) {
            log.warn("API key has expired");
            return Optional.empty();
        }

        log.debug("API key validated successfully");
        return apiKeyInfo;
    }

    /**
     * Revoke an API key
     *
     * @param apiKeyId the API key ID
     * @param revokedBy the user who revoked the key
     */
    @Auditable(action = "Revoke API Key", resourceType = "api_key", operation = Operation.DELETE)
    public void revokeApiKey(String apiKeyId, String revokedBy) {
        log.info("Revoking API key: '{}'", apiKeyId);

        transactionTemplate.inTransaction(WRITE, handle -> {
            var dao = handle.attach(ApiKeyDAO.class);

            var apiKey = dao.findById(apiKeyId)
                    .orElseThrow(() -> new NotFoundException("API key not found: '%s'".formatted(apiKeyId)));

            if (apiKey.status() == ApiKeyStatus.REVOKED) {
                log.warn("API key is already revoked: '{}'", apiKeyId);
                return null;
            }

            dao.updateStatus(apiKeyId, ApiKeyStatus.REVOKED, revokedBy);

            log.info("API key revoked successfully: '{}'", apiKeyId);
            return null;
        });
    }

    /**
     * Update last used time (async)
     *
     * @param apiKeyId the API key ID
     */
    public void updateLastUsedAsync(String apiKeyId) {
        CompletableFuture.runAsync(() -> {
            try {
                transactionTemplate.inTransaction(WRITE, handle -> {
                    handle.attach(ApiKeyDAO.class).updateLastUsed(apiKeyId, Instant.now());
                    return null;
                });
            } catch (Exception e) {
                log.error("Failed to update API key last used time: '{}'", apiKeyId, e);
            }
        });
    }

    /**
     * List API keys for a user in a workspace
     *
     * @param userId the user ID
     * @param workspaceId the workspace ID
     * @return list of API keys
     */
    public List<ApiKey> listApiKeys(String userId, String workspaceId) {
        log.info("Listing API keys for user: '{}' in workspace: '{}'", userId, workspaceId);
        return transactionTemplate.inTransaction(READ_ONLY, handle -> {
            return handle.attach(ApiKeyDAO.class).findByUserAndWorkspace(userId, workspaceId);
        });
    }

    /**
     * List API keys with pagination, sorting and filtering
     *
     * @param userId the user ID
     * @param workspaceId the workspace ID
     * @param page the page number (1-based)
     * @param size the page size
     * @param search search by name
     * @param status filter by status
     * @param sortBy sort by field
     * @param sortDir sort direction
     * @return paginated list of API keys
     */
    public ApiKeyPage listApiKeysPaged(String userId, String workspaceId, int page, int size,
            String search, String status, String sortBy, String sortDir) {
        log.info("Listing API keys (paged) for user: '{}' in workspace: '{}'", userId, workspaceId);

        // Validate and normalize parameters
        int validPage = Math.max(1, page);
        int validSize = Math.min(Math.max(1, size), 100); // Max 100 items per page
        int offset = (validPage - 1) * validSize;

        // Validate sort field to prevent SQL injection
        String validSortBy = switch (sortBy != null ? sortBy.toLowerCase() : "created_at") {
            case "name" -> "name";
            case "status" -> "status";
            case "last_used_at" -> "last_used_at";
            default -> "created_at";
        };

        String validSortDir = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";

        // Parse status filter
        ApiKeyStatus statusFilter = null;
        if (StringUtils.isNotBlank(status)) {
            try {
                statusFilter = ApiKeyStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status filter: '{}'", status);
            }
        }

        final ApiKeyStatus finalStatusFilter = statusFilter;
        final String finalSearch = StringUtils.isNotBlank(search) ? "%" + search.trim() + "%" : null;

        return transactionTemplate.inTransaction(READ_ONLY, handle -> {
            var dao = handle.attach(ApiKeyDAO.class);

            List<ApiKey> content = dao.findByUserAndWorkspacePaged(
                    userId, workspaceId, finalSearch, finalStatusFilter,
                    validSortBy, validSortDir, validSize, offset);

            long total = dao.countByUserAndWorkspace(userId, workspaceId, finalSearch, finalStatusFilter);

            return ApiKeyPage.builder()
                    .content(content)
                    .page(validPage)
                    .size(validSize)
                    .total(total)
                    .build();
        });
    }

    /**
     * Check if an API key has a specific scope
     *
     * @param apiKey the API key to check
     * @param requiredScope the required scope
     * @return true if the API key has the scope, false otherwise
     */
    public boolean checkScope(ApiKey apiKey, String requiredScope) {
        if (apiKey.scopes() == null || apiKey.scopes().isEmpty()) {
            // No scopes defined means full access
            log.debug("API key '{}' has full access (no scopes defined)", apiKey.id());
            return true;
        }

        // Check for wildcard scope (full access)
        if (apiKey.scopes().contains("*")) {
            log.debug("API key '{}' has wildcard scope (full access)", apiKey.id());
            return true;
        }

        // Check for exact scope match
        boolean hasScope = apiKey.scopes().contains(requiredScope);
        log.debug("API key '{}' scope check for '{}': '{}'", apiKey.id(), requiredScope, hasScope);
        return hasScope;
    }

    /**
     * Check if an API key has read-only access
     *
     * @param apiKey the API key to check
     * @return true if the API key has only read scope, false otherwise
     */
    public boolean isReadOnly(ApiKey apiKey) {
        if (apiKey.scopes() == null || apiKey.scopes().isEmpty()) {
            return false; // No scopes means full access
        }

        if (apiKey.scopes().contains("*")) {
            return false; // Wildcard means full access
        }

        // Read-only if only contains "read" scope
        return apiKey.scopes().size() == 1 && apiKey.scopes().contains("read");
    }

    /**
     * Generate a secure random API key
     */
    private String generateSecureApiKey() {
        byte[] randomBytes = new byte[API_KEY_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return API_KEY_PREFIX + randomPart;
    }

    /**
     * Hash an API key using SHA-256
     */
    private String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Result object containing both plain API key and entity
     */
    public record ApiKeyResult(String plainApiKey, ApiKey apiKey) {
    }
}
