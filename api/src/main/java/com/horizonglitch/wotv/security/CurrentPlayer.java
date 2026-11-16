package com.horizonglitch.wotv.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the authenticated player from the access token. */
public final class CurrentPlayer {

	private CurrentPlayer() {
	}

	/** The token subject is the player id. */
	public static long id(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}
}
