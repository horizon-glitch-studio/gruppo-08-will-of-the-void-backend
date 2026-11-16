package com.horizonglitch.wotv.services;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.horizonglitch.wotv.exceptions.ApiException;

@Service
public class PlayerIdentityService {

	private final JdbcTemplate jdbcTemplate;

	public PlayerIdentityService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public long requirePlayerId(String username) {
		if (username == null || username.isBlank()) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "An authenticated player is required.");
		}

		ListResult result = jdbcTemplate.query(
				"SELECT id FROM player WHERE lower(username) = lower(?) AND deleted_at IS NULL",
				rs -> rs.next() ? new ListResult(rs.getLong("id")) : null,
				username);
		if (result == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "PLAYER_NOT_FOUND", "Player was not found.");
		}
		return result.id();
	}

	private record ListResult(long id) {
	}
}
