package com.workhub.workspace;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

	@Query("""
			select new com.workhub.workspace.WorkspaceResponse(w.id, w.name, w.description, w.logoUrl, w.createdAt, m.role)
			from WorkspaceMember m join Workspace w on w.id = m.workspaceId
			where m.userId = :userId and m.guest = false
			order by lower(w.name)
			""")
	List<WorkspaceResponse> findAllForMember(@Param("userId") UUID userId);

}
