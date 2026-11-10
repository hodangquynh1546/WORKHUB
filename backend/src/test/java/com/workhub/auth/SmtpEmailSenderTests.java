package com.workhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;
import java.util.concurrent.Executors;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;

import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailSenderTests {

	private final JavaMailSender mail = mock(JavaMailSender.class);

	private final SmtpEmailSender sender = new SmtpEmailSender(mail, "WORKHUB <no-reply@example.com>", new DirectExecutor());

	@Test
	void buildsAUtf8PlainTextMessageAndHandsItToTheServer() throws Exception {
		MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
		when(mail.createMimeMessage()).thenReturn(message);

		sender.send("an@example.com", "Xác minh email", "Nhấp vào liên kết: https://x.example/verify-email?token=abc");

		verify(mail).send(message);
		assertThat(message.getSubject()).isEqualTo("Xác minh email");
		assertThat(message.getAllRecipients()[0].toString()).isEqualTo("an@example.com");
		assertThat(message.getFrom()[0].toString()).contains("no-reply@example.com");
		assertThat(message.getContent().toString()).contains("verify-email?token=abc");
	}

	@Test
	void aFailingServerIsLoggedButNeverThrownIntoTheRequest() throws Exception {
		MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
		when(mail.createMimeMessage()).thenReturn(message);
		org.mockito.Mockito.doThrow(new MailSendException("smtp down")).when(mail).send(any(MimeMessage.class));

		sender.send("an@example.com", "s", "b");

		verify(mail).send(message);
	}

	@Test
	void doesNothingWhenTheExecutorIsShutDown() {
		var executor = Executors.newSingleThreadExecutor();
		executor.shutdown();
		new SmtpEmailSender(mail, "a@b.c", executor).shutdown();
		verify(mail, never()).send(any(MimeMessage.class));
	}

	/** Runs the task on the calling thread so the test can assert right after send(). */
	private static final class DirectExecutor extends java.util.concurrent.AbstractExecutorService {

		@Override
		public void execute(Runnable command) {
			command.run();
		}

		@Override
		public void shutdown() {
		}

		@Override
		public java.util.List<Runnable> shutdownNow() {
			return java.util.List.of();
		}

		@Override
		public boolean isShutdown() {
			return false;
		}

		@Override
		public boolean isTerminated() {
			return false;
		}

		@Override
		public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) {
			return true;
		}

	}

}
