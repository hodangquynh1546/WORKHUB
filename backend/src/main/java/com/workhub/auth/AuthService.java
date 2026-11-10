package com.workhub.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.workhub.common.ApiException;
import com.workhub.security.JwtService;
import com.workhub.user.User;
import com.workhub.user.UserProfile;
import com.workhub.user.UserRepository;
import com.workhub.user.UserService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

	/** bcrypt ignores input beyond 72 bytes, and Spring Security rejects it. */
	private static final int MAX_PASSWORD_BYTES = 72;

	private final UserRepository users;

	private final UserService userService;

	private final UserTokenService tokens;

	private final PasswordEncoder passwordEncoder;

	private final JwtService jwtService;

	private final EmailSender emailSender;

	private final AuthProperties properties;

	/** Compared against when the email is unknown, so login takes the same time either way. */
	private final String dummyPasswordHash;

	public AuthService(UserRepository users, UserService userService, UserTokenService tokens,
			PasswordEncoder passwordEncoder, JwtService jwtService, EmailSender emailSender, AuthProperties properties) {
		this.users = users;
		this.userService = userService;
		this.tokens = tokens;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.emailSender = emailSender;
		this.properties = properties;
		this.dummyPasswordHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
	}

	/** A started session: the response body plus the raw refresh token for the cookie. */
	public record Session(AuthResponse response, String refreshToken) {
	}

	public Session register(AuthRequests.Register request) {
		String email = User.normalizeEmail(request.email());
		if (users.existsByEmail(email)) {
			throw ApiException.conflict("EMAIL_TAKEN", "Email is already registered");
		}
		checkPassword(request.password());
		User user = users.save(new User(email, passwordEncoder.encode(request.password()), request.fullName().strip()));
		sendVerificationEmail(user);
		return startSession(user);
	}

	public Session login(AuthRequests.Login request) {
		User user = users.findByEmail(User.normalizeEmail(request.email())).orElse(null);
		boolean matches = passwordEncoder.matches(request.password(),
				(user != null) ? user.getPasswordHash() : dummyPasswordHash);
		if (user == null || !matches) {
			throw ApiException.unauthorized("INVALID_CREDENTIALS", "Email or password is incorrect");
		}
		return startSession(user);
	}

	/** Rotates the refresh token. Commits even when it throws, so reuse-detection revocations stick. */
	@Transactional(noRollbackFor = ApiException.class)
	public Session refresh(String rawRefreshToken) {
		UserToken token = tokens.consume(TokenType.REFRESH, rawRefreshToken);
		User user = users.findById(token.getUserId())
			.orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired"));
		return startSession(user);
	}

	public void logout(String rawRefreshToken) {
		if (rawRefreshToken != null) {
			tokens.revoke(TokenType.REFRESH, rawRefreshToken);
		}
	}

	public void verifyEmail(String rawToken) {
		UserToken token = tokens.consume(TokenType.EMAIL_VERIFICATION, rawToken);
		userService.require(token.getUserId()).markEmailVerified();
	}

	public void resendVerification(UUID userId) {
		User user = userService.require(userId);
		if (!user.isEmailVerified()) {
			sendVerificationEmail(user);
		}
	}

	/** Always succeeds, so the response does not reveal whether the email is registered. */
	public void forgotPassword(AuthRequests.ForgotPassword request) {
		users.findByEmail(User.normalizeEmail(request.email())).ifPresent(this::sendPasswordResetEmail);
	}

	/** Sets a new password and signs the user out everywhere. */
	public void resetPassword(AuthRequests.ResetPassword request) {
		checkPassword(request.newPassword());
		UserToken token = tokens.consume(TokenType.PASSWORD_RESET, request.token());
		User user = userService.require(token.getUserId());
		user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
		// Following the emailed link proves the user owns the address.
		user.markEmailVerified();
		tokens.revokeAll(user.getId(), TokenType.REFRESH);
	}

	/** Changes the password, signs out all other sessions and starts a fresh one for the caller. */
	public Session changePassword(UUID userId, AuthRequests.ChangePassword request) {
		User user = userService.require(userId);
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
		}
		checkPassword(request.newPassword());
		user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
		tokens.revokeAll(userId, TokenType.REFRESH);
		return startSession(user);
	}

	private Session startSession(User user) {
		String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail());
		String refreshToken = tokens.issue(user.getId(), TokenType.REFRESH);
		AuthResponse response = new AuthResponse(accessToken, "Bearer", jwtService.accessTokenTtl().toSeconds(),
				UserProfile.from(user));
		return new Session(response, refreshToken);
	}

	private void sendVerificationEmail(User user) {
		String token = tokens.issue(user.getId(), TokenType.EMAIL_VERIFICATION);
		String link = properties.frontendBaseUrl() + "/verify-email?token=" + token;
		emailSender.send(user.getEmail(), "Xác thực email WORKHUB",
				"Xin chào " + user.getFullName() + ",\n\nNhấn vào liên kết sau để xác thực email của bạn:\n" + link
						+ "\n\nLiên kết có hiệu lực trong " + properties.emailVerificationTtl().toHours() + " giờ.");
	}

	private void sendPasswordResetEmail(User user) {
		String token = tokens.issue(user.getId(), TokenType.PASSWORD_RESET);
		String link = properties.frontendBaseUrl() + "/reset-password?token=" + token;
		emailSender.send(user.getEmail(), "Đặt lại mật khẩu WORKHUB",
				"Xin chào " + user.getFullName() + ",\n\nNhấn vào liên kết sau để đặt lại mật khẩu:\n" + link
						+ "\n\nLiên kết có hiệu lực trong " + properties.passwordResetTtl().toMinutes()
						+ " phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.");
	}

	private static void checkPassword(String password) {
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
			throw ApiException.badRequest("PASSWORD_TOO_LONG", "Password must be at most 72 bytes");
		}
	}

}
