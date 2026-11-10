package com.workhub.document;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

	/** Rows: folder id (null = not in a folder), number of non-deleted documents. */
	@Query("select d.folderId, count(d) from Document d where d.projectId = :projectId and d.status = com.workhub.document.DocumentStatus.ACTIVE group by d.folderId")
	List<Object[]> countsByFolder(@Param("projectId") UUID projectId);

	boolean existsByFolderId(UUID folderId);

}
