package com.comet.opik.domain;

import com.comet.opik.api.User;
import com.comet.opik.api.UserStatus;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.Instant;
import java.util.Optional;

@RegisterRowMapper(UserRowMapper.class)
public interface UserDAO {

    @SqlUpdate("""
            INSERT INTO users (
                id, username, email, password_hash, full_name, avatar_url,
                status, is_system_admin, email_verified, locale, version,
                created_at, created_by, last_updated_at, last_updated_by
            ) VALUES (
                :id, :username, :email, :passwordHash, :fullName, :avatarUrl,
                :status, :systemAdmin, :emailVerified, :locale, :version,
                :createdAt, :createdBy, :lastUpdatedAt, :lastUpdatedBy
            )
            """)
    @GetGeneratedKeys
    void insert(@BindBean User user);

    @SqlQuery("""
            SELECT * FROM users WHERE id = :id
            """)
    Optional<User> findById(@Bind("id") String id);

    @SqlQuery("""
            SELECT * FROM users WHERE username = :username
            """)
    Optional<User> findByUsername(@Bind("username") String username);

    @SqlQuery("""
            SELECT * FROM users WHERE email = :email
            """)
    Optional<User> findByEmail(@Bind("email") String email);

    @SqlQuery("""
            SELECT EXISTS(SELECT 1 FROM users WHERE username = :username)
            """)
    boolean existsByUsername(@Bind("username") String username);

    @SqlQuery("""
            SELECT EXISTS(SELECT 1 FROM users WHERE email = :email)
            """)
    boolean existsByEmail(@Bind("email") String email);

    @SqlUpdate("""
            UPDATE users
            SET password_hash = :passwordHash,
                version = version + 1,
                last_updated_at = CURRENT_TIMESTAMP(6),
                last_updated_by = :updatedBy
            WHERE id = :id
            """)
    void updatePassword(
            @Bind("id") String id,
            @Bind("passwordHash") String passwordHash,
            @Bind("updatedBy") String updatedBy);

    @SqlUpdate("""
            UPDATE users
            SET email = :email,
                full_name = :fullName,
                avatar_url = :avatarUrl,
                locale = :locale,
                version = version + 1,
                last_updated_at = CURRENT_TIMESTAMP(6),
                last_updated_by = :updatedBy
            WHERE id = :id
            """)
    void update(
            @Bind("id") String id,
            @Bind("email") String email,
            @Bind("fullName") String fullName,
            @Bind("avatarUrl") String avatarUrl,
            @Bind("locale") String locale,
            @Bind("updatedBy") String updatedBy);

    @SqlUpdate("""
            UPDATE users
            SET status = :status,
                version = version + 1,
                last_updated_at = CURRENT_TIMESTAMP(6),
                last_updated_by = :updatedBy
            WHERE id = :id
            """)
    void updateStatus(
            @Bind("id") String id,
            @Bind("status") UserStatus status,
            @Bind("updatedBy") String updatedBy);

    @SqlUpdate("""
            UPDATE users
            SET last_login_at = :lastLoginAt,
                last_updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = :id
            """)
    void updateLastLogin(
            @Bind("id") String id,
            @Bind("lastLoginAt") Instant lastLoginAt);
}
