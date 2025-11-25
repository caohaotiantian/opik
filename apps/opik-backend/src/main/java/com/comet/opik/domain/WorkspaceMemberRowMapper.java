package com.comet.opik.domain;

import com.comet.opik.api.MemberStatus;
import com.comet.opik.api.WorkspaceMember;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WorkspaceMemberRowMapper implements RowMapper<WorkspaceMember> {

    @Override
    public WorkspaceMember map(ResultSet rs, StatementContext ctx) throws SQLException {
        return WorkspaceMember.builder()
                .id(rs.getString("id"))
                .workspaceId(rs.getString("workspace_id"))
                .userId(rs.getString("user_id"))
                .roleId(rs.getString("role_id"))
                .status(MemberStatus.valueOf(rs.getString("status")))
                .version(rs.getInt("version"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .createdBy(rs.getString("created_by"))
                .lastUpdatedAt(rs.getTimestamp("last_updated_at").toInstant())
                .lastUpdatedBy(rs.getString("last_updated_by"))
                .build();
    }
}
