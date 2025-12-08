package com.comet.opik.domain;

import com.comet.opik.api.Role;
import com.comet.opik.api.RoleScope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class RoleRowMapper implements RowMapper<Role> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    @SneakyThrows
    public Role map(ResultSet rs, StatementContext ctx) throws SQLException {
        String permissionsJson = rs.getString("permissions");
        Set<String> permissions = permissionsJson != null
                ? OBJECT_MAPPER.readValue(permissionsJson, new TypeReference<Set<String>>() {
                })
                : new HashSet<>();

        return Role.builder()
                .id(rs.getString("id"))
                .name(rs.getString("name"))
                .displayName(rs.getString("display_name"))
                .description(rs.getString("description"))
                .scope(RoleScope.valueOf(rs.getString("scope").toUpperCase()))
                .builtin(rs.getBoolean("is_builtin"))
                .permissions(permissions)
                .version(rs.getInt("version"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .createdBy(rs.getString("created_by"))
                .lastUpdatedAt(rs.getTimestamp("last_updated_at").toInstant())
                .lastUpdatedBy(rs.getString("last_updated_by"))
                .build();
    }
}
