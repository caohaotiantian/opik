package com.comet.opik.domain;

import com.comet.opik.api.PasswordResetStatus;
import com.comet.opik.api.PasswordResetToken;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

public class PasswordResetTokenRowMapper implements RowMapper<PasswordResetToken> {

    @Override
    public PasswordResetToken map(ResultSet rs, StatementContext ctx) throws SQLException {
        return PasswordResetToken.builder()
                .id(rs.getString("id"))
                .userId(rs.getString("user_id"))
                .token(rs.getString("token"))
                .status(PasswordResetStatus.fromValue(rs.getString("status")))
                .ipAddress(rs.getString("ip_address"))
                .usedAt(toInstant(rs.getTimestamp("used_at")))
                .expiresAt(toInstant(rs.getTimestamp("expires_at")))
                .createdAt(toInstant(rs.getTimestamp("created_at")))
                .createdBy(rs.getString("created_by"))
                .lastUpdatedAt(toInstant(rs.getTimestamp("last_updated_at")))
                .lastUpdatedBy(rs.getString("last_updated_by"))
                .build();
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
