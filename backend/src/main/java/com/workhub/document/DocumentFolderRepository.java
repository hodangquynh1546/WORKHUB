package com.workhub.document;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentFolderRepository extends JpaRepository<DocumentFolder, UUID> {

	List<DocumentFolder> findAllByProjectIdOrderByNameAsc(UUID projectId);

	boolean existsByParentId(UUID parentId);

}
