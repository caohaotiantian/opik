package com.comet.opik.domain;

import com.comet.opik.api.MemberStatus;
import com.comet.opik.api.ProjectMember;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;

@RegisterRowMapper(ProjectMemberRowMapper.class)
public interface ProjectMemberDAO {

    @SqlUpdate("""
            INSERT INTO project_members (
                id, project_id, user_id, role_id, status, version,
                created_at, created_by, last_updated_at, last_updated_by
            ) VALUES (
                :id, :projectId, :userId, :roleId, :status, :version,
                :createdAt, :createdBy, :lastUpdatedAt, :lastUpdatedBy
            )
            """)
    void insert(@BindMethods ProjectMember member);

    @SqlQuery("""
            SELECT * FROM project_members
            WHERE project_id = :projectId AND user_id = :userId
            """)
    Optional<ProjectMember> findByProjectAndUser(
            @Bind("projectId") String projectId,
            @Bind("userId") String userId);

    @SqlQuery("""
            SELECT * FROM project_members WHERE project_id = :projectId
            """)
    List<ProjectMember> findByProject(@Bind("projectId") String projectId);

    @SqlQuery("""
            SELECT * FROM project_members WHERE user_id = :userId
            """)
    List<ProjectMember> findByUser(@Bind("userId") String userId);

    @SqlQuery("""
            SELECT EXISTS(
                SELECT 1 FROM project_members
                WHERE project_id = :projectId AND user_id = :userId
            )
            """)
    boolean exists(
            @Bind("projectId") String projectId,
            @Bind("userId") String userId);

    @SqlUpdate("""
            UPDATE project_members
            SET role_id = :roleId,
                version = version + 1,
                last_updated_at = CURRENT_TIMESTAMP(6),
                last_updated_by = :updatedBy
            WHERE project_id = :projectId AND user_id = :userId
            """)
    void updateRole(
            @Bind("projectId") String projectId,
            @Bind("userId") String userId,
            @Bind("roleId") String roleId,
            @Bind("updatedBy") String updatedBy);

    @SqlUpdate("""
            UPDATE project_members
            SET status = :status,
                version = version + 1,
                last_updated_at = CURRENT_TIMESTAMP(6),
                last_updated_by = :updatedBy
            WHERE project_id = :projectId AND user_id = :userId
            """)
    void updateStatus(
            @Bind("projectId") String projectId,
            @Bind("userId") String userId,
            @Bind("status") MemberStatus status,
            @Bind("updatedBy") String updatedBy);

    @SqlUpdate("""
            DELETE FROM project_members
            WHERE project_id = :projectId AND user_id = :userId
            """)
    void delete(
            @Bind("projectId") String projectId,
            @Bind("userId") String userId);
}
