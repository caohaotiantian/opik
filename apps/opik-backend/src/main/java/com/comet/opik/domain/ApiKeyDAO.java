package com.comet.opik.domain;

import com.comet.opik.api.ApiKey;
import com.comet.opik.api.ApiKeyStatus;
import com.comet.opik.infrastructure.db.SetFlatArgumentFactory;
import org.jdbi.v3.sqlobject.config.RegisterArgumentFactory;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RegisterRowMapper(ApiKeyRowMapper.class)
@RegisterArgumentFactory(SetFlatArgumentFactory.class)
public interface ApiKeyDAO {

    @SqlUpdate("""
            INSERT INTO user_api_keys (
                id, user_id, workspace_id, name, key_hash, key_prefix,
                status, permissions, expires_at,
                created_at, created_by, last_updated_at, last_updated_by
            ) VALUES (
                :id, :userId, :workspaceId, :name, :keyHash, :keyPrefix,
                :status, :scopes, :expiresAt,
                :createdAt, :createdBy, :lastUpdatedAt, :lastUpdatedBy
            )
            """)
    void insert(@BindMethods ApiKey apiKey);

    @SqlQuery("""
            SELECT * FROM user_api_keys WHERE id = :id
            """)
    Optional<ApiKey> findById(@Bind("id") String id);

    @SqlQuery("""
            SELECT * FROM user_api_keys WHERE key_hash = :keyHash
            """)
    Optional<ApiKey> findByKeyHash(@Bind("keyHash") String keyHash);

    @SqlQuery("""
            SELECT * FROM user_api_keys
            WHERE user_id = :userId AND workspace_id = :workspaceId
            """)
    List<ApiKey> findByUserAndWorkspace(
            @Bind("userId") String userId,
            @Bind("workspaceId") String workspaceId);

    @SqlQuery("""
            SELECT COUNT(*) FROM user_api_keys
            WHERE user_id = :userId AND status = 'ACTIVE'
            """)
    int countActiveByUser(@Bind("userId") String userId);

    @SqlUpdate("""
            UPDATE user_api_keys
            SET status = :status,
                last_updated_at = CURRENT_TIMESTAMP(6),
                last_updated_by = :updatedBy
            WHERE id = :id
            """)
    void updateStatus(
            @Bind("id") String id,
            @Bind("status") ApiKeyStatus status,
            @Bind("updatedBy") String updatedBy);

    @SqlUpdate("""
            UPDATE user_api_keys
            SET last_used_at = :lastUsedAt,
                last_updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = :id
            """)
    void updateLastUsed(
            @Bind("id") String id,
            @Bind("lastUsedAt") Instant lastUsedAt);
}
