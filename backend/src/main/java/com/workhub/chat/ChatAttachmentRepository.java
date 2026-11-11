package com.workhub.chat;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, UUID> {

	@Query("select new com.workhub.chat.ChatAttachment$Info(a.messageId, a.id, a.fileName, a.contentType, a.sizeBytes, a.position) from ChatAttachment a where a.messageId in :ids order by a.position")
	List<ChatAttachment.Info> infoFor(@Param("ids") Collection<UUID> ids);

}
