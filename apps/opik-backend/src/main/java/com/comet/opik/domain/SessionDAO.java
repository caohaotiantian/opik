package com.comet.opik.domain;

import com.comet.opik.api.Session;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.Instant;
import java.util.Optional;

@RegisterRowMapper(SessionRowMapper.class)
public interface SessionDAO {

    @SqlUpdate("""
            INSERT INTO user_sessions (
                id, user_id, token_hash, fingerprint, ip_address, user_agent,
                expires_at, last_activity_at, version,
                created_at, created_by, last_updated_at, last_updated_by
            ) VALUES (
                :id, :userId, :sessionToken, :fingerprint, :ipAddress, :userAgent,
                :expiresAt, :lastActivityAt, :version,
                :createdAt, :createdBy, :lastUpdatedAt, :lastUpdatedBy
            )
            """)
    @GetGeneratedKeys
    void insert(@BindBean Session session);

    @SqlQuery("""
            SELECT * FROM user_sessions WHERE id = :id
            """)
    Optional<Session> findById(@Bind("id") String id);

    @SqlQuery("""
            SELECT * FROM user_sessions WHERE token_hash = :tokenHash
            """)
    Optional<Session> findByTokenHash(@Bind("tokenHash") String tokenHash);

    @SqlQuery("""
            SELECT COUNT(*) FROM user_sessions
            WHERE user_id = :userId AND expires_at > CURRENT_TIMESTAMP(6)
            """)
    int countActiveByUser(@Bind("userId") String userId);

    @SqlUpdate("""
            UPDATE user_sessions
            SET last_activity_at = :lastActivityAt,
                last_updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = :id
            """)
    void updateLastActivity(
            @Bind("id") String id,
            @Bind("lastActivityAt") Instant lastActivityAt);

    @SqlUpdate("""
            DELETE FROM user_sessions WHERE token_hash = :tokenHash
            """)
    void deleteByTokenHash(@Bind("tokenHash") String tokenHash);

    @SqlUpdate("""
            DELETE FROM user_sessions WHERE expires_at < :now
            """)
    int deleteExpired(@Bind("now") Instant now);

    @SqlUpdate("""
            DELETE FROM user_sessions
            WHERE user_id = :userId
            AND id NOT IN (
                SELECT id FROM user_sessions
                WHERE user_id = :userId
                ORDER BY created_at DESC
                LIMIT :keepCount
            )
            """)
    int deleteOldUserSessions(
            @Bind("userId") String userId,
            @Bind("keepCount") int keepCount);
}
