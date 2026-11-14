package com.workhub.integration;

import java.util.UUID;

import com.workhub.auth.AuthRequests;
import com.workhub.auth.AuthService;
import com.workhub.security.CurrentUser;
import com.workhub.user.UserProfile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets an external system (ForgeBridge, the VS Code extension) act as a WorkHub user. {@code connect} and
 * {@code token} are public (see SecurityConfig); the rest need a normal bearer token.
 */
@RestController
public class IntegrationController {

	private final AuthService auth;

	private final IntegrationTokenService tokens;

	public IntegrationController(AuthService auth, IntegrationTokenService tokens) {
		this.auth = auth;
		this.tokens = tokens;
	}

	public record Connect(@NotBlank @Email @Size(max = 320) String email, @NotNull @Size(min = 8, max = 72) String password,
			Boolean register, @Size(max = 150) String fullName, @Size(max = 100) String tokenName) {
	}

	public record Connected(String token, UserProfile user, boolean registered) {
	}

	public record Exchange(@NotBlank String token) {
	}

	public record NewToken(@Size(max = 100) String name) {
	}

	/** Signs in (or registers) with email and password and returns a long-lived integration token. */
	@PostMapping("/api/integrations/connect")
	@ResponseStatus(HttpStatus.CREATED)
	public Connected connect(@Valid @RequestBody Connect request) {
		boolean registering = Boolean.TRUE.equals(request.register());
		AuthService.Session session = registering
				? auth.register(new AuthRequests.Register(request.email(), request.password(),
						(request.fullName() == null || request.fullName().isBlank()) ? request.email() : request.fullName()))
				: auth.login(new AuthRequests.Login(request.email(), request.password()));
		UserProfile user = session.response().user();
		IntegrationTokenService.Created created = tokens.create(user.id(), request.tokenName());
		return new Connected(created.token(), user, registering);
	}

	@PostMapping("/api/integrations/token")
	public IntegrationTokenService.Exchanged exchange(@Valid @RequestBody Exchange request) {
		return tokens.exchange(request.token());
	}

	@PostMapping("/api/integrations/tokens")
	@ResponseStatus(HttpStatus.CREATED)
	public IntegrationTokenService.Created create(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) NewToken body) {
		return tokens.create(CurrentUser.id(jwt), (body == null) ? null : body.name());
	}

	@DeleteMapping("/api/integrations/tokens/{tokenId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tokenId) {
		tokens.revoke(CurrentUser.id(jwt), tokenId);
	}

}
