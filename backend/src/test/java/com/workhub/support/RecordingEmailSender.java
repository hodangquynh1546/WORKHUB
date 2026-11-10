package com.workhub.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.workhub.auth.EmailSender;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Replaces the logging sender in every test context so tests can read the links that were "emailed". */
@Component
@Primary
public class RecordingEmailSender implements EmailSender {

	private static final Pattern TOKEN = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");

	public record Email(String to, String subject, String body) {
	}

	private final List<Email> sent = new CopyOnWriteArrayList<>();

	@Override
	public void send(String to, String subject, String body) {
		sent.add(new Email(to, subject, body));
	}

	/** Token from the most recent email to {@code to} whose link path contains {@code pathPart}. */
	public String lastToken(String to, String pathPart) {
		for (int i = sent.size() - 1; i >= 0; i--) {
			Email email = sent.get(i);
			if (email.to().equals(to) && email.body().contains(pathPart)) {
				Matcher matcher = TOKEN.matcher(email.body());
				if (matcher.find()) {
					return matcher.group(1);
				}
			}
		}
		throw new AssertionError("No email to " + to + " containing " + pathPart);
	}

	public long countTo(String to) {
		return sent.stream().filter((email) -> email.to().equals(to)).count();
	}

}
