package com.horizonglitch.wotv.common.mail;

/** Sends an email to a Summoner. */
public interface EmailSender {

	void send(String to, String subject, String body);
}
