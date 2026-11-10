package com.workhub.auth;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends plain-text email through the SMTP server configured with {@code SMTP_*} / {@code MAIL_FROM}. Delivery runs on
 * its own thread and a failure is only logged: a slow or broken mail server must never fail a sign-up or a request.
 */
@Component
@ConditionalOnProperty(name = "workhub.mail.enabled", havingValue = "true")
class SmtpEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

	private final JavaMailSender mailSender;

	private final String from;

	private final ExecutorService executor;

	@org.springframework.beans.factory.annotation.Autowired
	SmtpEmailSender(JavaMailSender mailSender, @Value("${workhub.mail.from}") String from) {
		this(mailSender, from, Executors.newSingleThreadExecutor((task) -> {
			Thread thread = new Thread(task, "email-sender");
			thread.setDaemon(true);
			return thread;
		}));
	}

	SmtpEmailSender(JavaMailSender mailSender, String from, ExecutorService executor) {
		this.mailSender = mailSender;
		this.from = from;
		this.executor = executor;
	}

	@Override
	public void send(String to, String subject, String body) {
		executor.execute(() -> {
			try {
				MimeMessage message = mailSender.createMimeMessage();
				MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
				helper.setFrom(from);
				helper.setTo(to);
				helper.setSubject(subject);
				helper.setText(body, false);
				mailSender.send(message);
			}
			catch (Exception ex) {
				log.error("Could not send email to {} ({})", to, subject, ex);
			}
		});
	}

	@PreDestroy
	void shutdown() {
		executor.shutdown();
	}

}
