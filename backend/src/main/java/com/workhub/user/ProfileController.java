package com.workhub.user;

import java.io.IOException;

import jakarta.validation.Valid;

import com.workhub.auth.AuthRequests;
import com.workhub.auth.AuthResponse;
import com.workhub.auth.AuthService;
import com.workhub.auth.RefreshCookies;
import com.workhub.security.CurrentUser;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** The signed-in user's own account. */
@RestController
@RequestMapping("/api/me")
public class ProfileController {

	private final UserService userService;

	private final AuthService authService;

	private final RefreshCookies refreshCookies;

	private final AccountDeletionService accountDeletion;

	public ProfileController(UserService userService, AuthService authService, RefreshCookies refreshCookies,
			AccountDeletionService accountDeletion) {
		this.accountDeletion = accountDeletion;
		this.userService = userService;
		this.authService = authService;
		this.refreshCookies = refreshCookies;
	}

	@GetMapping
	public UserProfile get(@AuthenticationPrincipal Jwt jwt) {
		return userService.getProfile(CurrentUser.id(jwt));
	}

	@PutMapping
	public UserProfile update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
		return userService.updateProfile(CurrentUser.id(jwt), request);
	}

	@PutMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public UserProfile uploadAvatar(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file)
			throws IOException {
		return userService.uploadAvatar(CurrentUser.id(jwt), file.getContentType(), file.getBytes());
	}

	@DeleteMapping("/avatar")
	public UserProfile removeAvatar(@AuthenticationPrincipal Jwt jwt) {
		return userService.removeAvatar(CurrentUser.id(jwt));
	}

	/** Anonymises the account (see {@link AccountDeletionService}) and clears the caller's refresh cookie. */
	@DeleteMapping
	public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AuthRequests.DeleteAccount request) {
		accountDeletion.delete(CurrentUser.id(jwt), request.password());
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString()).build();
	}

	/** Other sessions are signed out; the caller gets a new session in the response. */
	@PostMapping("/password")
	public ResponseEntity<AuthResponse> changePassword(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody AuthRequests.ChangePassword request) {
		AuthService.Session session = authService.changePassword(CurrentUser.id(jwt), request);
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, refreshCookies.issue(session.refreshToken()).toString())
			.body(session.response());
	}

	@PostMapping("/email-verification")
	public ResponseEntity<Void> resendVerification(@AuthenticationPrincipal Jwt jwt) {
		authService.resendVerification(CurrentUser.id(jwt));
		return ResponseEntity.noContent().build();
	}

}
