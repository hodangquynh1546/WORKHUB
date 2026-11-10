package com.workhub.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Used while MAIL_ENABLED is not true (development): writes emails (including verification and reset links) to the application log. */
@Component
@ConditionalOnProperty(name = "workhub.mail.enabled", havingValue = "false", matchIfMissing = true)
class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	@Override
	public void send(String to, String subject, String body) {
		log.info("Email to {} | {}\n{}", to, subject, body);
	}

}
