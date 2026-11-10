package com.workhub.document;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentLinkRepository extends JpaRepository<DocumentLink, UUID> {

	List<DocumentLink> findAllByDocumentId(UUID documentId);

	boolean existsByDocumentIdAndTargetTypeAndTargetId(UUID documentId, LinkTargetType type, UUID targetId);

	long deleteByDocumentIdAndTargetTypeAndTargetId(UUID documentId, LinkTargetType type, UUID targetId);

}
