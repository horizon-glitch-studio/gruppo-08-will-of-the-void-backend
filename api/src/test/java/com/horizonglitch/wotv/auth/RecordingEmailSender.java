package com.horizonglitch.wotv.auth;

import com.horizonglitch.wotv.common.mail.EmailSender;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps the emails sent during a test, so the tests can read the tokens in the links. */
public class RecordingEmailSender implements EmailSender {

	private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

	private final List<String[]> sent = new ArrayList<>();

	@Override
	public synchronized void send(String to, String subject, String body) {
		sent.add(new String[] {to, subject, body});
	}

	/** The token in the last email sent to that address whose subject contains the given text. */
	public synchronized String lastToken(String to, String subjectPart) {
		for (int i = sent.size() - 1; i >= 0; i--) {
			String[] mail = sent.get(i);
			if (mail[0].equalsIgnoreCase(to) && mail[1].contains(subjectPart)) {
				Matcher matcher = TOKEN.matcher(mail[2]);
				if (matcher.find()) {
					return matcher.group(1);
				}
			}
		}
		throw new AssertionError("No email '" + subjectPart + "' sent to " + to);
	}
}
