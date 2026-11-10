package com.workhub.user;

import jakarta.validation.Valid;

import com.workhub.auth.AuthRequests;
import com.workhub.auth.AuthResponse;
import com.workhub.auth.AuthService;
import com.workhub.auth.RefreshCookies;
import com.workhub.security.CurrentUser;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own account. */
@RestController
@RequestMapping("/api/me")
public class ProfileController {

	private final UserService userService;

	private final AuthService authService;

	private final RefreshCookies refreshCookies;

	public ProfileController(UserService userService, AuthService authService, RefreshCookies refreshCookies) {
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
