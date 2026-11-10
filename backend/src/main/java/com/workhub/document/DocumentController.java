package com.workhub.document;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.workhub.common.PageResponse;
import com.workhub.document.DocumentResponses.AttachmentResponse;
import com.workhub.document.DocumentResponses.DocumentDetail;
import com.workhub.document.DocumentResponses.DocumentSummary;
import com.workhub.document.DocumentResponses.FolderResponse;
import com.workhub.document.DocumentResponses.VersionDetail;
import com.workhub.document.DocumentResponses.VersionSummary;
import com.workhub.security.CurrentUser;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
public class DocumentController {

	private final DocumentService documents;

	private final DocumentFolderService folders;

	private final DocumentAttachmentService attachments;

	public DocumentController(DocumentService documents, DocumentFolderService folders,
			DocumentAttachmentService attachments) {
		this.documents = documents;
		this.folders = folders;
		this.attachments = attachments;
	}

	// ---- folders ----

	@GetMapping("/api/projects/{projectId}/document-folders")
	public List<FolderResponse> folders(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
		return folders.list(CurrentUser.id(jwt), projectId);
	}

	@PostMapping("/api/projects/{projectId}/document-folders")
	@ResponseStatus(HttpStatus.CREATED)
	public FolderResponse createFolder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@Valid @RequestBody DocumentRequests.SaveFolder request) {
		return folders.create(CurrentUser.id(jwt), projectId, request);
	}

	@PutMapping("/api/document-folders/{folderId}")
	public FolderResponse renameFolder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID folderId,
			@Valid @RequestBody DocumentRequests.SaveFolder request) {
		return folders.rename(CurrentUser.id(jwt), folderId, request);
	}

	@DeleteMapping("/api/document-folders/{folderId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteFolder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID folderId) {
		folders.delete(CurrentUser.id(jwt), folderId);
	}

	// ---- documents ----

	@GetMapping("/api/projects/{projectId}/documents")
	public PageResponse<DocumentSummary> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@RequestParam(required = false) String q, @RequestParam(required = false) List<DocumentType> type,
			@RequestParam(required = false) List<DocumentStatus> status, @RequestParam(required = false) UUID folderId,
			@RequestParam(defaultValue = "false") boolean unfiled,
			@RequestParam(required = false) LinkTargetType linkedType, @RequestParam(required = false) UUID linkedId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		DocumentFilter filter = new DocumentFilter(q, type, status, folderId, unfiled, linkedType, linkedId);
		return documents.list(CurrentUser.id(jwt), projectId, filter, page, size);
	}

	@PostMapping("/api/projects/{projectId}/documents")
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentDetail create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@Valid @RequestBody DocumentRequests.Create request) {
		return documents.create(CurrentUser.id(jwt), projectId, request);
	}

	@GetMapping("/api/documents/{documentId}")
	public DocumentDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId) {
		return documents.get(CurrentUser.id(jwt), documentId);
	}

	@PutMapping("/api/documents/{documentId}")
	public DocumentDetail update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId,
			@Valid @RequestBody DocumentRequests.Update request) {
		return documents.update(CurrentUser.id(jwt), documentId, request);
	}

	@PostMapping("/api/documents/{documentId}/archive")
	public DocumentDetail archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId) {
		return documents.setArchived(CurrentUser.id(jwt), documentId, true);
	}

	@PostMapping("/api/documents/{documentId}/unarchive")
	public DocumentDetail unarchive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId) {
		return documents.setArchived(CurrentUser.id(jwt), documentId, false);
	}

	@DeleteMapping("/api/documents/{documentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId) {
		documents.delete(CurrentUser.id(jwt), documentId);
	}

	// ---- versions ----

	@GetMapping("/api/documents/{documentId}/versions")
	public List<VersionSummary> versions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId) {
		return documents.versions(CurrentUser.id(jwt), documentId);
	}

	@GetMapping("/api/documents/{documentId}/versions/{number}")
	public VersionDetail version(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId,
			@PathVariable int number) {
		return documents.version(CurrentUser.id(jwt), documentId, number);
	}

	@PostMapping("/api/documents/{documentId}/versions/{number}/restore")
	public DocumentDetail restore(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId,
			@PathVariable int number) {
		return documents.restore(CurrentUser.id(jwt), documentId, number);
	}

	// ---- links ----

	@PostMapping("/api/documents/{documentId}/links")
	public DocumentDetail addLink(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId,
			@Valid @RequestBody DocumentRequests.AddLink request) {
		return documents.addLink(CurrentUser.id(jwt), documentId, request);
	}

	@DeleteMapping("/api/documents/{documentId}/links/{type}/{targetId}")
	public DocumentDetail removeLink(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId,
			@PathVariable LinkTargetType type, @PathVariable UUID targetId) {
		return documents.removeLink(CurrentUser.id(jwt), documentId, type, targetId);
	}

	// ---- attachments ----

	@PostMapping(path = "/api/documents/{documentId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public AttachmentResponse upload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId,
			@RequestParam("file") MultipartFile file) throws java.io.IOException {
		return attachments.upload(CurrentUser.id(jwt), documentId, file.getOriginalFilename(), file.getContentType(),
				file.getBytes());
	}

	/** Always sent as a download, never rendered by the browser, whatever type the uploader claimed. */
	@GetMapping("/api/documents/{documentId}/attachments/{attachmentId}")
	public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId,
			@PathVariable UUID attachmentId) {
		DocumentAttachment file = attachments.download(CurrentUser.id(jwt), documentId, attachmentId);
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(file.getFileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
			.body(file.getData());
	}

	@DeleteMapping("/api/documents/{documentId}/attachments/{attachmentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteAttachment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId,
			@PathVariable UUID attachmentId) {
		attachments.delete(CurrentUser.id(jwt), documentId, attachmentId);
	}

}
