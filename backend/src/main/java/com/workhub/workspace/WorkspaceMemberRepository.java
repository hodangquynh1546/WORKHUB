package com.workhub.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

	Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

	boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

	@Query("""
			select new com.workhub.workspace.WorkspaceMemberResponse(u.id, u.email, u.fullName, u.avatarUrl, m.role, m.joinedAt)
			from WorkspaceMember m join User u on u.id = m.userId
			where m.workspaceId = :workspaceId
			order by m.joinedAt
			""")
	List<WorkspaceMemberResponse> findMembers(@Param("workspaceId") UUID workspaceId);

}
