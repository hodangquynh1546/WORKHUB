package com.workhub.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/** Development stand-in: writes emails (including verification and reset links) to the application log. */
@Component
class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	@Override
	public void send(String to, String subject, String body) {
		log.info("Email to {} | {}\n{}", to, subject, body);
	}

}
