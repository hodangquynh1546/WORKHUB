package com.workhub.auth;

/** Outgoing email. {@link SmtpEmailSender} when {@code MAIL_ENABLED=true}, otherwise {@link LoggingEmailSender}. */
public interface EmailSender {

	void send(String to, String subject, String body);

}
