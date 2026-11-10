package com.workhub.issue;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.workhub.issue.IssueResponses.ChecklistItemResponse;
import com.workhub.issue.IssueResponses.CommentResponse;
import com.workhub.security.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IssueCollaborationController {

	private final IssueCollaborationService service;

	public IssueCollaborationController(IssueCollaborationService service) {
		this.service = service;
	}

	@GetMapping("/api/issues/{issueId}/comments")
	public List<CommentResponse> comments(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID issueId) {
		return service.listComments(CurrentUser.id(jwt), issueId);
	}

	@PostMapping("/api/issues/{issueId}/comments")
	@ResponseStatus(HttpStatus.CREATED)
	public CommentResponse addComment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID issueId,
			@Valid @RequestBody IssueRequests.CommentBody request) {
		return service.addComment(CurrentUser.id(jwt), issueId, request.body(), request.mentions());
	}

	@PutMapping("/api/comments/{commentId}")
	public CommentResponse editComment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID commentId,
			@Valid @RequestBody IssueRequests.CommentBody request) {
		return service.editComment(CurrentUser.id(jwt), commentId, request.body());
	}

	@DeleteMapping("/api/comments/{commentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteComment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID commentId) {
		service.deleteComment(CurrentUser.id(jwt), commentId);
	}

	@PostMapping("/api/issues/{issueId}/checklist")
	@ResponseStatus(HttpStatus.CREATED)
	public ChecklistItemResponse addItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID issueId,
			@Valid @RequestBody IssueRequests.ChecklistAdd request) {
		return service.addChecklistItem(CurrentUser.id(jwt), issueId, request.text());
	}

	@PutMapping("/api/checklist/{itemId}")
	public ChecklistItemResponse updateItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itemId,
			@Valid @RequestBody IssueRequests.ChecklistUpdate request) {
		return service.updateChecklistItem(CurrentUser.id(jwt), itemId, request);
	}

	@DeleteMapping("/api/checklist/{itemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itemId) {
		service.deleteChecklistItem(CurrentUser.id(jwt), itemId);
	}

}
