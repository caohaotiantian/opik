package com.comet.opik.domain;

import com.comet.opik.api.ApiKey;
import com.comet.opik.api.ApiKeyStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class ApiKeyRowMapper implements RowMapper<ApiKey> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    @SneakyThrows
    public ApiKey map(ResultSet rs, StatementContext ctx) throws SQLException {
        String scopesJson = rs.getString("scopes");
        Set<String> scopes = scopesJson != null
                ? OBJECT_MAPPER.readValue(scopesJson, new TypeReference<Set<String>>() {
                })
                : new HashSet<>();

        return ApiKey.builder()
                .id(rs.getString("id"))
                .userId(rs.getString("user_id"))
                .workspaceId(rs.getString("workspace_id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .keyHash(rs.getString("api_key_hash"))
                .keyPrefix(rs.getString("key_prefix"))
                .status(ApiKeyStatus.valueOf(rs.getString("status")))
                .scopes(scopes)
                .expiresAt(rs.getTimestamp("expires_at") != null
                        ? rs.getTimestamp("expires_at").toInstant()
                        : null)
                .lastUsedAt(rs.getTimestamp("last_used_at") != null
                        ? rs.getTimestamp("last_used_at").toInstant()
                        : null)
                .version(rs.getInt("version"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .createdBy(rs.getString("created_by"))
                .lastUpdatedAt(rs.getTimestamp("last_updated_at").toInstant())
                .lastUpdatedBy(rs.getString("last_updated_by"))
                .build();
    }
}
