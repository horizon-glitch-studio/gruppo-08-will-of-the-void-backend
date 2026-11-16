package com.horizonglitch.wotv.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Development sender: writes the email to the log instead of sending it, so the verification and reset links
 * can be copied from the console. To be replaced by an SMTP sender before release.
 */
@Component
public class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	@Override
	public void send(String to, String subject, String body) {
		log.info("Email to {} - {}\n{}", to, subject, body);
	}
}
