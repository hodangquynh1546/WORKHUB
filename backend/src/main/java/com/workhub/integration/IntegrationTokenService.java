package com.workhub.integration;

import java.time.Clock;
import java.util.UUID;

import com.workhub.common.ApiException;
import com.workhub.common.SecureTokens;
import com.workhub.security.JwtService;
import com.workhub.user.User;
import com.workhub.user.UserProfile;
import com.workhub.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IntegrationTokenService {

	static final String PREFIX = "whk_";

	private final IntegrationTokenRepository tokens;

	private final UserRepository users;

	private final JwtService jwt;

	private final Clock clock;

	public IntegrationTokenService(IntegrationTokenRepository tokens, UserRepository users, JwtService jwt, Clock clock) {
		this.tokens = tokens;
		this.users = users;
		this.jwt = jwt;
		this.clock = clock;
	}

	public record Created(UUID id, String token) {
	}

	public record Exchanged(String accessToken, String tokenType, long expiresIn, UserProfile user) {
	}

	/** The raw token is returned once and never stored. */
	public Created create(UUID userId, String name) {
		String raw = PREFIX + SecureTokens.newToken();
		IntegrationToken saved = tokens
			.save(new IntegrationToken(userId, (name == null || name.isBlank()) ? "integration" : name.strip(),
					SecureTokens.hash(raw), clock.instant()));
		return new Created(saved.getId(), raw);
	}

	/** Trades a still-valid integration token for a normal short-lived access token of its owner. */
	public Exchanged exchange(String raw) {
		IntegrationToken token = (raw == null || !raw.startsWith(PREFIX)) ? null
				: tokens.findByTokenHash(SecureTokens.hash(raw)).orElse(null);
		if (token == null || token.isRevoked()) {
			throw ApiException.unauthorized("INVALID_INTEGRATION_TOKEN", "Integration token is invalid or revoked");
		}
		User user = users.findById(token.getUserId())
			.orElseThrow(() -> ApiException.unauthorized("INVALID_INTEGRATION_TOKEN", "Integration token is invalid or revoked"));
		token.used(clock.instant());
		return new Exchanged(jwt.issueAccessToken(user.getId(), user.getEmail()), "Bearer",
				jwt.accessTokenTtl().toSeconds(), UserProfile.from(user));
	}

	public void revoke(UUID userId, UUID tokenId) {
		IntegrationToken token = tokens.findById(tokenId).filter((t) -> t.getUserId().equals(userId))
			.orElseThrow(() -> ApiException.notFound("TOKEN_NOT_FOUND", "Token not found"));
		token.revoke(clock.instant());
	}

}
