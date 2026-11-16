package com.horizonglitch.wotv.auth;

/** Purpose of a {@link PlayerToken}. Stored as text; the values match the CHECK constraint of the column. */
public enum TokenType {
	EMAIL_VERIFICATION, PASSWORD_RESET, REFRESH
}
