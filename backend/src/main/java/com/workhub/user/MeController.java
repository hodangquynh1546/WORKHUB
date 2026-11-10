package com.workhub.user;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Returns the identity carried by the caller's access token. */
@RestController
public class MeController {

	@GetMapping("/api/me")
	public Map<String, String> me(@AuthenticationPrincipal Jwt jwt) {
		return Map.of("id", jwt.getSubject(), "email", jwt.getClaimAsString("email"));
	}

}
