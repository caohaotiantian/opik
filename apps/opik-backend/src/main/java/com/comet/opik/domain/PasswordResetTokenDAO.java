package com.comet.opik.domain;

import com.comet.opik.api.PasswordResetStatus;
import com.comet.opik.api.PasswordResetToken;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.Instant;
import java.util.Optional;

@RegisterRowMapper(PasswordResetTokenRowMapper.class)
public interface PasswordResetTokenDAO {

    @SqlUpdate("""
            INSERT INTO password_reset_tokens (
                id, user_id, token, status, ip_address, expires_at,
                created_at, created_by, last_updated_at, last_updated_by
            ) VALUES (
                :id, :userId, :token, :status, :ipAddress, :expiresAt,
                :createdAt, :createdBy, :lastUpdatedAt, :lastUpdatedBy
            )
            """)
    void insert(@BindMethods PasswordResetToken token);

    @SqlQuery("""
            SELECT * FROM password_reset_tokens WHERE id = :id
            """)
    Optional<PasswordResetToken> findById(@Bind("id") String id);

    @SqlQuery("""
            SELECT * FROM password_reset_tokens WHERE token = :token
            """)
    Optional<PasswordResetToken> findByToken(@Bind("token") String token);

    @SqlQuery("""
            SELECT * FROM password_reset_tokens
            WHERE token = :token AND status = 'pending' AND expires_at > :now
            """)
    Optional<PasswordResetToken> findValidToken(@Bind("token") String token, @Bind("now") Instant now);

    @SqlUpdate("""
            UPDATE password_reset_tokens
            SET status = :status,
                used_at = :usedAt,
                last_updated_at = CURRENT_TIMESTAMP(6),
                last_updated_by = :updatedBy
            WHERE id = :id
            """)
    void updateStatus(
            @Bind("id") String id,
            @Bind("status") PasswordResetStatus status,
            @Bind("usedAt") Instant usedAt,
            @Bind("updatedBy") String updatedBy);

    @SqlUpdate("""
            DELETE FROM password_reset_tokens WHERE expires_at < :now
            """)
    int deleteExpired(@Bind("now") Instant now);

    @SqlUpdate("""
            UPDATE password_reset_tokens
            SET status = 'expired',
                last_updated_at = CURRENT_TIMESTAMP(6),
                last_updated_by = 'system'
            WHERE user_id = :userId AND status = 'pending'
            """)
    void invalidateAllUserTokens(@Bind("userId") String userId);
}
