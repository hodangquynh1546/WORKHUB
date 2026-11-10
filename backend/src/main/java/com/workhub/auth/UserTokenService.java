package com.workhub.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.workhub.common.ApiException;
import com.workhub.common.SecureTokens;

import org.springframework.stereotype.Service;

/**
 * Issues and redeems opaque tokens (refresh, email verification, password reset).
 * Not transactional on purpose: it always runs inside the caller's transaction, and
 * {@link AuthService#refresh} must be able to commit the reuse-detection revocation
 * while still throwing.
 */
@Service
class UserTokenService {

	private final UserTokenRepository tokens;

	private final AuthProperties properties;

	private final Clock clock;

	UserTokenService(UserTokenRepository tokens, AuthProperties properties, Clock clock) {
		this.tokens = tokens;
		this.properties = properties;
		this.clock = clock;
	}

	/** Returns the raw token to hand to the user. One-time tokens replace any earlier unused one of the same type. */
	String issue(UUID userId, TokenType type) {
		Instant now = clock.instant();
		if (type != TokenType.REFRESH) {
			tokens.revokeAll(userId, type, now);
		}
		String raw = SecureTokens.newToken();
		tokens.save(new UserToken(userId, type, SecureTokens.hash(raw), now.plus(ttl(type))));
		return raw;
	}

	/**
	 * Marks the token used and returns it. Presenting an already-used refresh token means it leaked
	 * (the legitimate client rotated it), so every refresh token of that user is revoked. A revoked
	 * token (logout, password change) is only rejected: another device may simply still hold it.
	 */
	UserToken consume(TokenType type, String raw) {
		Instant now = clock.instant();
		UserToken token = (raw == null) ? null : tokens.findByTypeAndTokenHash(type, SecureTokens.hash(raw)).orElse(null);
		if (token == null || token.isRevoked()) {
			throw invalid(type);
		}
		if (token.isConsumed()) {
			if (type == TokenType.REFRESH) {
				tokens.revokeAll(token.getUserId(), TokenType.REFRESH, now);
			}
			throw invalid(type);
		}
		if (token.isExpired(now)) {
			throw invalid(type);
		}
		token.consume(now);
		return token;
	}

	/** Invalidates one token without reuse detection (used by logout). */
	void revoke(TokenType type, String raw) {
		tokens.findByTypeAndTokenHash(type, SecureTokens.hash(raw))
			.filter((token) -> !token.isConsumed() && !token.isRevoked())
			.ifPresent((token) -> token.revoke(clock.instant()));
	}

	void revokeAll(UUID userId, TokenType type) {
		tokens.revokeAll(userId, type, clock.instant());
	}

	Duration ttl(TokenType type) {
		return switch (type) {
			case REFRESH -> properties.refreshTokenTtl();
			case EMAIL_VERIFICATION -> properties.emailVerificationTtl();
			case PASSWORD_RESET -> properties.passwordResetTtl();
		};
	}

	private static ApiException invalid(TokenType type) {
		return (type == TokenType.REFRESH)
				? ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired")
				: ApiException.badRequest("INVALID_TOKEN", "Token is invalid or expired");
	}

}
