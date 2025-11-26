package com.comet.opik.domain;

import com.comet.opik.api.Workspace;
import com.comet.opik.api.WorkspaceStatus;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;

@RegisterRowMapper(WorkspaceRowMapper.class)
public interface WorkspaceDAO {

    @SqlUpdate("""
            INSERT INTO workspaces (
                id, name, display_name, description, status, owner_user_id,
                quota_limit, allow_public_access, max_members, settings, version,
                created_at, created_by, last_updated_at, last_updated_by
            ) VALUES (
                :id, :name, :displayName, :description, :status, :ownerUserId,
                :quotaLimit, :allowPublicAccess, :maxMembers, :settings, :version,
                :createdAt, :createdBy, :lastUpdatedAt, :lastUpdatedBy
            )
            """)
    void insert(@BindMethods Workspace workspace);

    @SqlQuery("""
            SELECT * FROM workspaces WHERE id = :id
            """)
    Optional<Workspace> findById(@Bind("id") String id);

    @SqlQuery("""
            SELECT * FROM workspaces WHERE name = :name
            """)
    Optional<Workspace> findByName(@Bind("name") String name);

    @SqlQuery("""
            SELECT EXISTS(SELECT 1 FROM workspaces WHERE name = :name)
            """)
    boolean existsByName(@Bind("name") String name);

    @SqlQuery("""
            SELECT w.* FROM workspaces w
            INNER JOIN workspace_members wm ON w.id = wm.workspace_id
            WHERE wm.user_id = :userId AND w.status = 'ACTIVE'
            """)
    List<Workspace> findByUserId(@Bind("userId") String userId);

    @SqlUpdate("""
            UPDATE workspaces
            SET name = :name,
                display_name = :displayName,
                description = :description,
                quota_limit = :quotaLimit,
                max_members = :maxMembers,
                settings = :settings,
                version = version + 1,
                last_updated_at = CURRENT_TIMESTAMP(6),
                last_updated_by = :updatedBy
            WHERE id = :id
            """)
    void update(
            @Bind("id") String id,
            @Bind("name") String name,
            @Bind("displayName") String displayName,
            @Bind("description") String description,
            @Bind("quotaLimit") Integer quotaLimit,
            @Bind("maxMembers") Integer maxMembers,
            @Bind("settings") String settings,
            @Bind("updatedBy") String updatedBy);

    @SqlUpdate("""
            UPDATE workspaces
            SET status = :status,
                version = version + 1,
                last_updated_at = CURRENT_TIMESTAMP(6),
                last_updated_by = :updatedBy
            WHERE id = :id
            """)
    void updateStatus(
            @Bind("id") String id,
            @Bind("status") WorkspaceStatus status,
            @Bind("updatedBy") String updatedBy);
}
