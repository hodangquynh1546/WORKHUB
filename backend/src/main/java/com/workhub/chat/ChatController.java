package com.workhub.chat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import com.workhub.chat.ChatResponses.ConversationSummary;
import com.workhub.chat.ChatResponses.MessagePage;
import com.workhub.chat.ChatResponses.MessageResponse;
import com.workhub.chat.ChatResponses.SearchHit;
import com.workhub.issue.IssueResponses.UserRef;
import com.workhub.security.CurrentUser;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

	private final ChatService chat;

	public ChatController(ChatService chat) {
		this.chat = chat;
	}

	@GetMapping("/api/chat/conversations")
	public List<ConversationSummary> conversations(@AuthenticationPrincipal Jwt jwt) {
		return retryOnRace(() -> chat.list(CurrentUser.id(jwt)));
	}

	@PostMapping("/api/chat/projects/{projectId}")
	public ConversationSummary openProject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
		return retryOnRace(() -> chat.openProject(CurrentUser.id(jwt), projectId));
	}

	@PostMapping("/api/chat/direct")
	public ConversationSummary openDirect(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChatRequests.OpenDirect request) {
		return retryOnRace(() -> chat.openDirect(CurrentUser.id(jwt), request.userId(), request.email()));
	}

	/** Finds one person by their exact email address, to start a conversation with them. */
	@GetMapping("/api/chat/find-person")
	public com.workhub.issue.IssueResponses.UserRef findPerson(@AuthenticationPrincipal Jwt jwt, @RequestParam String email) {
		return chat.findPerson(CurrentUser.id(jwt), email);
	}

	@PostMapping("/api/chat/groups")
	@ResponseStatus(HttpStatus.CREATED)
	public ConversationSummary createGroup(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChatRequests.CreateGroup request) {
		return chat.createGroup(CurrentUser.id(jwt), request.workspaceId(), request.title(), request.memberIds());
	}

	@PutMapping("/api/chat/groups/{id}")
	public ConversationSummary rename(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ChatRequests.Rename request) {
		return chat.rename(CurrentUser.id(jwt), id, request.title());
	}

	@PostMapping("/api/chat/groups/{id}/members")
	public ConversationSummary addMembers(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ChatRequests.AddMembers request) {
		return chat.addMembers(CurrentUser.id(jwt), id, request.memberIds());
	}

	@PostMapping("/api/chat/groups/{id}/leave")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void leave(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		chat.leave(CurrentUser.id(jwt), id);
	}

	@GetMapping("/api/chat/conversations/{id}/members")
	public List<UserRef> members(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return chat.membersOf(CurrentUser.id(jwt), id);
	}

	/** The newest {@code size} messages, or the ones before {@code before} (an ISO instant) to page back. */
	@GetMapping("/api/chat/conversations/{id}/messages")
	public MessagePage messages(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
			@RequestParam(defaultValue = "50") int size) {
		return chat.messages(CurrentUser.id(jwt), id, before, size);
	}

	@PostMapping("/api/chat/conversations/{id}/messages")
	@ResponseStatus(HttpStatus.CREATED)
	public MessageResponse send(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ChatRequests.Send request) {
		return chat.send(CurrentUser.id(jwt), id, request);
	}

	@PostMapping(path = "/api/chat/conversations/{id}/attachments", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public MessageResponse sendFiles(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@RequestParam("files") List<org.springframework.web.multipart.MultipartFile> files,
			@RequestParam(value = "body", required = false) String body,
			@RequestParam(value = "replyToId", required = false) UUID replyToId) throws java.io.IOException {
		List<ChatService.RawFile> raw = new java.util.ArrayList<>();
		for (org.springframework.web.multipart.MultipartFile file : files) {
			raw.add(new ChatService.RawFile(file.getOriginalFilename(), file.getContentType(), file.getBytes()));
		}
		return chat.sendFiles(CurrentUser.id(jwt), id, body, replyToId, raw);
	}

	@GetMapping("/api/chat/conversations/{id}/receipts")
	public List<ChatResponses.ReadReceipt> receipts(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return chat.receipts(CurrentUser.id(jwt), id);
	}

	/** Always sent as a download; the UI shows images from the blob it fetches here. */
	@GetMapping("/api/chat/attachments/{attachmentId}")
	public org.springframework.http.ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attachmentId) {
		ChatAttachment file = chat.attachment(CurrentUser.id(jwt), attachmentId);
		return org.springframework.http.ResponseEntity.ok()
			.contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
			.header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
					org.springframework.http.ContentDisposition.attachment().filename(file.getFileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
			.header("Cache-Control", "private, max-age=3600")
			.body(file.getData());
	}

	@PutMapping("/api/chat/messages/{id}")
	public MessageResponse edit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ChatRequests.Edit request) {
		return chat.edit(CurrentUser.id(jwt), id, request.body());
	}

	@DeleteMapping("/api/chat/messages/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		chat.delete(CurrentUser.id(jwt), id);
	}

	@PostMapping("/api/chat/messages/{id}/reactions")
	public MessageResponse react(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ChatRequests.React request) {
		return chat.react(CurrentUser.id(jwt), id, request.emoji());
	}

	@PostMapping("/api/chat/conversations/{id}/read")
	public Map<String, Long> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return Map.of("unread", chat.markRead(CurrentUser.id(jwt), id));
	}

	@GetMapping("/api/chat/unread-count")
	public Map<String, Long> unread(@AuthenticationPrincipal Jwt jwt) {
		return Map.of("unread", chat.unreadTotal(CurrentUser.id(jwt)));
	}

	@GetMapping("/api/chat/search")
	public List<SearchHit> search(@AuthenticationPrincipal Jwt jwt, @RequestParam String q,
			@RequestParam(required = false) UUID conversationId) {
		return chat.search(CurrentUser.id(jwt), q, conversationId);
	}

	/**
	 * Channels and read markers are created on first use, so two requests arriving together (a page that fires its
	 * effect twice, two tabs) can both try to insert the same row; the unique index refuses the second one. The
	 * retry then simply finds what the first one created.
	 */
	private static <T> T retryOnRace(java.util.function.Supplier<T> action) {
		try {
			return action.get();
		}
		catch (org.springframework.dao.DataIntegrityViolationException ex) {
			return action.get();
		}
	}

}
