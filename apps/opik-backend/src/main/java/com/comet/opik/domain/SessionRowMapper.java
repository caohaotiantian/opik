package com.comet.opik.domain;

import com.comet.opik.api.Session;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SessionRowMapper implements RowMapper<Session> {

    @Override
    public Session map(ResultSet rs, StatementContext ctx) throws SQLException {
        return Session.builder()
                .id(rs.getString("id"))
                .userId(rs.getString("user_id"))
                .sessionToken(rs.getString("session_token"))
                .fingerprint(rs.getString("fingerprint"))
                .ipAddress(rs.getString("ip_address"))
                .userAgent(rs.getString("user_agent"))
                .expiresAt(rs.getTimestamp("expires_at").toInstant())
                .lastActivityAt(rs.getTimestamp("last_activity_at").toInstant())
                .version(rs.getInt("version"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .createdBy(rs.getString("created_by"))
                .lastUpdatedAt(rs.getTimestamp("last_updated_at").toInstant())
                .lastUpdatedBy(rs.getString("last_updated_by"))
                .build();
    }
}
