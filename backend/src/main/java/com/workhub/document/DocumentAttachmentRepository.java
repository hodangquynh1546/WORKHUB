package com.workhub.document;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.workhub.document.DocumentAttachment.AttachmentRow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentAttachmentRepository extends JpaRepository<DocumentAttachment, UUID> {

	@Query("""
			select new com.workhub.document.DocumentAttachment$AttachmentRow(a.id, a.fileName, a.contentType, a.sizeBytes,
				a.uploadedBy, a.createdAt)
			from DocumentAttachment a where a.documentId = :documentId order by a.createdAt, a.id
			""")
	List<AttachmentRow> rows(@Param("documentId") UUID documentId);

	long countByDocumentId(UUID documentId);

	/** Rows: document id, number of attachments. */
	@Query("select a.documentId, count(a) from DocumentAttachment a where a.documentId in :ids group by a.documentId")
	List<Object[]> countsFor(@Param("ids") Collection<UUID> ids);

}
