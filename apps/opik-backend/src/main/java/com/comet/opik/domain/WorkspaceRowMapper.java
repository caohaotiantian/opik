package com.comet.opik.domain;

import com.comet.opik.api.Workspace;
import com.comet.opik.api.WorkspaceStatus;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WorkspaceRowMapper implements RowMapper<Workspace> {

    @Override
    public Workspace map(ResultSet rs, StatementContext ctx) throws SQLException {
        return Workspace.builder()
                .id(rs.getString("id"))
                .name(rs.getString("name"))
                .displayName(rs.getString("display_name"))
                .description(rs.getString("description"))
                .status(WorkspaceStatus.valueOf(rs.getString("status")))
                .ownerUserId(rs.getString("owner_user_id"))
                .quotaLimit(rs.getInt("quota_limit"))
                .allowPublicAccess(rs.getBoolean("allow_public_access"))
                .maxMembers(rs.getInt("max_members"))
                .settings(rs.getString("settings"))
                .version(rs.getInt("version"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .createdBy(rs.getString("created_by"))
                .lastUpdatedAt(rs.getTimestamp("last_updated_at").toInstant())
                .lastUpdatedBy(rs.getString("last_updated_by"))
                .build();
    }
}
