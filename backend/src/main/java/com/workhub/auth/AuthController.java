package com.workhub.auth;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoints (see {@code SecurityConfig}): they must not require an access token. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	private final RefreshCookies refreshCookies;

	public AuthController(AuthService authService, RefreshCookies refreshCookies) {
		this.authService = authService;
		this.refreshCookies = refreshCookies;
	}

	@PostMapping("/register")
	public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequests.Register request) {
		return respond(HttpStatus.CREATED, authService.register(request));
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequests.Login request) {
		return respond(HttpStatus.OK, authService.login(request));
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthResponse> refresh(
			@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
		return respond(HttpStatus.OK, authService.refresh(refreshToken));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
		authService.logout(refreshToken);
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString()).build();
	}

	@PostMapping("/verify-email")
	public ResponseEntity<Void> verifyEmail(@Valid @RequestBody AuthRequests.VerifyEmail request) {
		authService.verifyEmail(request.token());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/forgot-password")
	public ResponseEntity<Void> forgotPassword(@Valid @RequestBody AuthRequests.ForgotPassword request) {
		authService.forgotPassword(request);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/reset-password")
	public ResponseEntity<Void> resetPassword(@Valid @RequestBody AuthRequests.ResetPassword request) {
		authService.resetPassword(request);
		return ResponseEntity.noContent().build();
	}

	private ResponseEntity<AuthResponse> respond(HttpStatus status, AuthService.Session session) {
		return ResponseEntity.status(status)
			.header(HttpHeaders.SET_COOKIE, refreshCookies.issue(session.refreshToken()).toString())
			.body(session.response());
	}

}
