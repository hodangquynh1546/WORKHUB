package com.workhub.auth;

/** Outgoing email. Swap {@link LoggingEmailSender} for an SMTP implementation when one is configured. */
public interface EmailSender {

	void send(String to, String subject, String body);

}
