package com.comet.opik.domain;

import com.comet.opik.api.User;
import com.comet.opik.api.UserStatus;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class UserRowMapper implements RowMapper<User> {

    @Override
    public User map(ResultSet rs, StatementContext ctx) throws SQLException {
        return User.builder()
                .id(rs.getString("id"))
                .username(rs.getString("username"))
                .email(rs.getString("email"))
                .passwordHash(rs.getString("password_hash"))
                .fullName(rs.getString("full_name"))
                .avatarUrl(rs.getString("avatar_url"))
                .status(UserStatus.valueOf(rs.getString("status")))
                .systemAdmin(rs.getBoolean("is_system_admin"))
                .emailVerified(rs.getBoolean("email_verified"))
                .lastLoginAt(rs.getTimestamp("last_login_at") != null
                        ? rs.getTimestamp("last_login_at").toInstant()
                        : null)
                .locale(rs.getString("locale"))
                .version(rs.getInt("version"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .createdBy(rs.getString("created_by"))
                .lastUpdatedAt(rs.getTimestamp("last_updated_at").toInstant())
                .lastUpdatedBy(rs.getString("last_updated_by"))
                .build();
    }
}
