package com.workhub.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks the other system (SmartSpec, through ForgeBridge) whether an e-mail/password pair is valid there, so one
 * account works in both. Used at sign-in (adopt an account created or re-passworded over there) and at sign-up
 * (refuse an address that exists there with a different password). Never blocks sign-in when the peer is down.
 */
@Component
public class IdentityPeer {

	private static final Logger log = LoggerFactory.getLogger(IdentityPeer.class);

	public enum Status {
		OK, WRONG_PASSWORD, NOT_FOUND, UNAVAILABLE
	}

	public record Result(Status status, String fullName, boolean emailVerified, java.time.Instant passwordChangedAt) {

		static Result unavailable() {
			return new Result(Status.UNAVAILABLE, null, false, null);
		}

		public boolean ok() {
			return status == Status.OK;
		}

	}

	private final IntegrationProperties properties;

	private final JsonMapper json;

	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

	IdentityPeer(IntegrationProperties properties, JsonMapper json) {
		this.properties = properties;
		this.json = json;
	}

	/** Tells the other system the password just changed here (best effort; the timestamp guard covers a failure). */
	public void setPassword(String email, String password) {
		if (properties.identityUrl().isEmpty()) {
			return;
		}
		try {
			String url = properties.identityUrl().replaceAll("/verify$", "/set-password");
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(6))
				.header("Content-Type", "application/json")
				.header(IntegrationGate.HEADER, properties.webhookSecret())
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("email", email, "password", password))))
				.build();
			client.send(request, HttpResponse.BodyHandlers.discarding());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		catch (Exception ex) {
			log.debug("Could not pass the new password to the peer: {}", ex.toString());
		}
	}

	public Result verify(String email, String password) {
		if (properties.identityUrl().isEmpty()) {
			return Result.unavailable();
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(properties.identityUrl()))
				.timeout(Duration.ofSeconds(6))
				.header("Content-Type", "application/json")
				.header(IntegrationGate.HEADER, properties.webhookSecret())
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("email", email, "password", password))))
				.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				return Result.unavailable();
			}
			JsonNode body = json.readTree(response.body());
			String status = body.path("status").asString("unavailable");
			String name = body.hasNonNull("fullName") ? body.path("fullName").asString() : null;
			boolean verified = body.path("emailVerified").asBoolean(false);
			java.time.Instant changedAt = null;
			if (body.hasNonNull("passwordChangedAt")) {
				try {
					changedAt = java.time.Instant.parse(body.path("passwordChangedAt").asString());
				}
				catch (RuntimeException ignored) {
					// an unreadable timestamp is treated as unknown
				}
			}
			return switch (status) {
				case "ok" -> new Result(Status.OK, name, verified, changedAt);
				case "wrong_password" -> new Result(Status.WRONG_PASSWORD, null, false, null);
				case "not_found" -> new Result(Status.NOT_FOUND, null, false, null);
				default -> Result.unavailable();
			};
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return Result.unavailable();
		}
		catch (Exception ex) {
			log.debug("Identity peer unavailable: {}", ex.toString());
			return Result.unavailable();
		}
	}

}
