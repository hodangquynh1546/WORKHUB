package com.workhub.chat;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The file of a chat message. Message lists use {@link Info} so the bytes are never loaded for them. */
@Entity
@Table(name = "chat_attachments")
public class ChatAttachment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "message_id", nullable = false, updatable = false)
	private UUID messageId;

	@Column(name = "file_name", nullable = false, length = 255)
	private String fileName;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@JdbcTypeCode(SqlTypes.VARBINARY)
	@Column(nullable = false)
	private byte[] data;

	/** Order of the file within its message. */
	@Column(nullable = false)
	private int position;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ChatAttachment() {
	}

	ChatAttachment(UUID messageId, int position, String fileName, String contentType, byte[] data) {
		this.messageId = messageId;
		this.position = position;
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = data.length;
		this.data = data;
	}

	public UUID getId() {
		return id;
	}

	public UUID getMessageId() {
		return messageId;
	}

	public String getFileName() {
		return fileName;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public byte[] getData() {
		return data;
	}

	/** Metadata only. */
	public record Info(UUID messageId, UUID id, String fileName, String contentType, long sizeBytes, int position) {
	}

}
