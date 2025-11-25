package com.comet.opik.domain;

import com.comet.opik.api.Role;
import com.comet.opik.api.RoleScope;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;

@RegisterRowMapper(RoleRowMapper.class)
public interface RoleDAO {

    @SqlUpdate("""
            INSERT INTO roles (
                id, name, display_name, description, scope, is_builtin, permissions, version,
                created_at, created_by, last_updated_at, last_updated_by
            ) VALUES (
                :id, :name, :displayName, :description, :scope, :builtin, :permissions::json, :version,
                :createdAt, :createdBy, :lastUpdatedAt, :lastUpdatedBy
            )
            """)
    @GetGeneratedKeys
    void insert(@BindBean Role role);

    @SqlQuery("""
            SELECT * FROM roles WHERE id = :id
            """)
    Optional<Role> findById(@Bind("id") String id);

    @SqlQuery("""
            SELECT * FROM roles WHERE name = :name AND scope = :scope
            """)
    Optional<Role> findByNameAndScope(@Bind("name") String name, @Bind("scope") RoleScope scope);

    @SqlQuery("""
            SELECT * FROM roles WHERE is_builtin = true
            """)
    List<Role> findBuiltinRoles();

    @SqlQuery("""
            SELECT * FROM roles WHERE scope = :scope
            """)
    List<Role> findByScope(@Bind("scope") RoleScope scope);

    @SqlUpdate("""
            UPDATE roles
            SET display_name = :displayName,
                description = :description,
                permissions = :permissions::json,
                version = version + 1,
                last_updated_at = CURRENT_TIMESTAMP(6),
                last_updated_by = :updatedBy
            WHERE id = :id AND is_builtin = false
            """)
    void update(
            @Bind("id") String id,
            @Bind("displayName") String displayName,
            @Bind("description") String description,
            @Bind("permissions") String permissions,
            @Bind("updatedBy") String updatedBy);
}
