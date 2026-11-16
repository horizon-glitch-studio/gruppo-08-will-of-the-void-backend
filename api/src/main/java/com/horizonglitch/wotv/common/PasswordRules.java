package com.horizonglitch.wotv.common;

import java.nio.charset.StandardCharsets;

/** Checks on passwords that annotations cannot express. */
public final class PasswordRules {

	/** BCrypt hashes at most 72 bytes: longer passwords are refused instead of being truncated. */
	public static final int MAX_BYTES = 72;

	private PasswordRules() {
	}

	public static boolean tooLong(String password) {
		return password != null && password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
	}
}
