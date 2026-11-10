package com.workhub.board;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.workhub.security.CurrentUser;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BoardController {

	private final BoardService boardService;

	private final WorkflowService workflowService;

	public BoardController(BoardService boardService, WorkflowService workflowService) {
		this.boardService = boardService;
		this.workflowService = workflowService;
	}

	@GetMapping("/api/projects/{projectId}/board")
	public BoardResponse board(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
		return boardService.get(CurrentUser.id(jwt), projectId);
	}

	/** All five statuses with their display names and whether the project uses them. */
	@GetMapping("/api/projects/{projectId}/workflow")
	public List<WorkflowEntry> workflow(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
		return workflowService.get(CurrentUser.id(jwt), projectId);
	}

	@PutMapping("/api/projects/{projectId}/workflow")
	public List<WorkflowEntry> updateWorkflow(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@Valid @RequestBody @NotNull @Size(min = 5, max = 5) List<@Valid WorkflowEntry> entries) {
		return workflowService.update(CurrentUser.id(jwt), projectId, entries);
	}

}
