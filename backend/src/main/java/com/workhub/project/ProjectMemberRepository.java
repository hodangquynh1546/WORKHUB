package com.workhub.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

	List<ProjectMember> findAllByProjectIdAndUserId(UUID projectId, UUID userId);

	boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

	List<ProjectMember> findAllByProjectIdAndRole(UUID projectId, ProjectRole role);

	boolean existsByProjectIdAndRole(UUID projectId, ProjectRole role);

	@Query("select count(distinct m.userId) from ProjectMember m where m.projectId = :projectId")
	long countByProjectId(@Param("projectId") UUID projectId);

	long countByProjectIdAndRole(UUID projectId, ProjectRole role);

	/** One row per held role; {@link ProjectService#listMembers} folds them into one entry per person. */
	@Query("""
			select new com.workhub.project.MemberRoleRow(u.id, u.email, u.fullName, u.avatarUrl, m.role, m.joinedAt)
			from ProjectMember m join User u on u.id = m.userId
			where m.projectId = :projectId
			order by lower(u.fullName), u.id
			""")
	List<MemberRoleRow> findMemberRoles(@Param("projectId") UUID projectId);

	@Query("select m.projectId, m.role from ProjectMember m where m.userId = :userId and m.projectId in :projectIds")
	List<Object[]> findRolesOfUser(@Param("userId") UUID userId, @Param("projectIds") java.util.Collection<UUID> projectIds);

	@Query("select distinct m.userId from ProjectMember m where m.projectId = :projectId")
	List<UUID> findUserIds(@Param("projectId") UUID projectId);

	@Query("select distinct m.userId from ProjectMember m where m.projectId = :projectId and m.role in :roles")
	List<UUID> findUserIdsByRole(@Param("projectId") UUID projectId, @Param("roles") java.util.Collection<ProjectRole> roles);

	/** Drops the user from every project of the workspace (used when they leave or are removed from it). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from ProjectMember m where m.userId = :userId and m.projectId in (select p.id from Project p where p.workspaceId = :workspaceId)")
	int deleteAllInWorkspace(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

}
