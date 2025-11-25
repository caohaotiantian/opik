package com.comet.opik.domain;

import com.comet.opik.api.MemberStatus;
import com.comet.opik.api.ProjectMember;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ProjectMemberRowMapper implements RowMapper<ProjectMember> {

    @Override
    public ProjectMember map(ResultSet rs, StatementContext ctx) throws SQLException {
        return ProjectMember.builder()
                .id(rs.getString("id"))
                .projectId(rs.getString("project_id"))
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
