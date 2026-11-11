package com.workhub.project;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import com.workhub.security.CurrentUser;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Project pictures, like profile pictures, are served without authentication because {@code <img>} requests cannot
 * carry the bearer token; ids are random UUIDs. Changing one needs UPDATE_PROJECT.
 */
@RestController
public class ProjectAvatarController {

	private final ProjectService projectService;

	public ProjectAvatarController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@PutMapping(path = "/api/projects/{projectId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ProjectResponse upload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
			@RequestParam("file") MultipartFile file) throws IOException {
		return projectService.uploadAvatar(CurrentUser.id(jwt), projectId, file.getContentType(), file.getBytes());
	}

	@DeleteMapping("/api/projects/{projectId}/avatar")
	public ProjectResponse remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
		return projectService.removeAvatar(CurrentUser.id(jwt), projectId);
	}

	@GetMapping("/api/public/projects/{projectId}/avatar")
	public ResponseEntity<byte[]> get(@PathVariable UUID projectId) {
		ProjectAvatar avatar = projectService.avatar(projectId);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(avatar.getContentType()))
			.cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
			.body(avatar.getData());
	}

}
