package com.workhub.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {

	private final RealtimeSocketHandler handler;

	private final String frontendBaseUrl;

	WebSocketConfig(RealtimeSocketHandler handler, @Value("${workhub.auth.frontend-base-url}") String frontendBaseUrl) {
		this.handler = handler;
		this.frontendBaseUrl = frontendBaseUrl;
	}

	/** Browsers send an Origin header; only the app's own frontend may open a socket. */
	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(handler, "/ws").setAllowedOrigins(frontendBaseUrl);
	}

}
