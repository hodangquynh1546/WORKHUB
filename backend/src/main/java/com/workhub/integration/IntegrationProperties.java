package com.workhub.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code workhub.integration.*}. {@code webhookUrl} blank = no outgoing webhooks. The body of each delivery is signed
 * with HMAC-SHA256 using {@code webhookSecret} (header {@code X-WorkHub-Signature: sha256=<hex>}).
 */
@ConfigurationProperties("workhub.integration")
public record IntegrationProperties(String webhookUrl, String webhookSecret, String identityUrl) {

	public IntegrationProperties {
		webhookUrl = (webhookUrl == null) ? "" : webhookUrl.strip();
		webhookSecret = (webhookSecret == null) ? "" : webhookSecret;
		identityUrl = (identityUrl == null) ? "" : identityUrl.strip();
	}

	boolean webhookEnabled() {
		return !webhookUrl.isEmpty();
	}

}
