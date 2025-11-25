package com.comet.opik.domain;

import com.comet.opik.api.MemberStatus;
import com.comet.opik.api.WorkspaceMember;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;

@RegisterRowMapper(WorkspaceMemberRowMapper.class)
public interface WorkspaceMemberDAO {

    @SqlUpdate("""
            INSERT INTO workspace_members (
                id, workspace_id, user_id, role_id, status, version,
                created_at, created_by, last_updated_at, last_updated_by
            ) VALUES (
                :id, :workspaceId, :userId, :roleId, :status, :version,
                :createdAt, :createdBy, :lastUpdatedAt, :lastUpdatedBy
            )
            """)
    @GetGeneratedKeys
    void insert(@BindBean WorkspaceMember member);

    @SqlQuery("""
            SELECT * FROM workspace_members
            WHERE workspace_id = :workspaceId AND user_id = :userId
            """)
    Optional<WorkspaceMember> findByWorkspaceAndUser(
            @Bind("workspaceId") String workspaceId,
            @Bind("userId") String userId);

    @SqlQuery("""
            SELECT * FROM workspace_members WHERE workspace_id = :workspaceId
            """)
    List<WorkspaceMember> findByWorkspace(@Bind("workspaceId") String workspaceId);

    @SqlQuery("""
            SELECT * FROM workspace_members WHERE user_id = :userId
            """)
    List<WorkspaceMember> findByUser(@Bind("userId") String userId);

    @SqlQuery("""
            SELECT EXISTS(
                SELECT 1 FROM workspace_members
                WHERE workspace_id = :workspaceId AND user_id = :userId
            )
            """)
    boolean exists(
            @Bind("workspaceId") String workspaceId,
            @Bind("userId") String userId);

    @SqlUpdate("""
            UPDATE workspace_members
            SET role_id = :roleId,
                version = version + 1,
                last_updated_at = CURRENT_TIMESTAMP(6),
                last_updated_by = :updatedBy
            WHERE workspace_id = :workspaceId AND user_id = :userId
            """)
    void updateRole(
            @Bind("workspaceId") String workspaceId,
            @Bind("userId") String userId,
            @Bind("roleId") String roleId,
            @Bind("updatedBy") String updatedBy);

    @SqlUpdate("""
            UPDATE workspace_members
            SET status = :status,
                version = version + 1,
                last_updated_at = CURRENT_TIMESTAMP(6),
                last_updated_by = :updatedBy
            WHERE workspace_id = :workspaceId AND user_id = :userId
            """)
    void updateStatus(
            @Bind("workspaceId") String workspaceId,
            @Bind("userId") String userId,
            @Bind("status") MemberStatus status,
            @Bind("updatedBy") String updatedBy);

    @SqlUpdate("""
            DELETE FROM workspace_members
            WHERE workspace_id = :workspaceId AND user_id = :userId
            """)
    void delete(
            @Bind("workspaceId") String workspaceId,
            @Bind("userId") String userId);
}
