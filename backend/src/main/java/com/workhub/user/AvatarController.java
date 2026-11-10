package com.workhub.user;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profile pictures are served without authentication because {@code <img>} requests cannot carry the bearer
 * token. Ids are random UUIDs and the image is the same one shown to everyone in a shared workspace.
 */
@RestController
@RequestMapping("/api/public/users")
public class AvatarController {

	private final UserService userService;

	public AvatarController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/{id}/avatar")
	public ResponseEntity<byte[]> get(@PathVariable UUID id) {
		UserAvatar avatar = userService.avatar(id);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(avatar.getContentType()))
			.cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
			.body(avatar.getData());
	}

}
