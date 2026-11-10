package com.workhub.chat;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ChatRequests {

	private ChatRequests() {
	}

	/** {@code mentionIds} are conversation members tagged with @; they are notified. */
	public record Send(@NotBlank @Size(max = ChatMessage.MAX_BODY) String body, UUID replyToId,
			@Size(max = 20) List<UUID> mentionIds) {
	}

	public record Edit(@NotBlank @Size(max = ChatMessage.MAX_BODY) String body) {
	}

	public record React(@NotBlank @Size(max = 16) String emoji) {
	}

	public record OpenDirect(@NotNull UUID workspaceId, @NotNull UUID userId) {
	}

	public record CreateGroup(@NotNull UUID workspaceId, @NotBlank @Size(max = 100) String title,
			@NotEmpty @Size(max = 50) List<UUID> memberIds) {
	}

	public record Rename(@NotBlank @Size(max = 100) String title) {
	}

	public record AddMembers(@NotEmpty @Size(max = 50) List<UUID> memberIds) {
	}

}
