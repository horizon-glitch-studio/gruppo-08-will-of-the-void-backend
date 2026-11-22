package com.horizonglitch.wotv.auth;

import com.horizonglitch.wotv.player.PlayerRole;

/** Response bodies of the auth routes. */
public final class AuthResponses {

	private AuthResponses() {
	}

	public record Registered(Long id, String username, String email, boolean emailVerified) {
	}

	public record Tokens(String tokenType, String accessToken, long accessTokenExpiresIn, String refreshToken,
			long refreshTokenExpiresIn) {
	}

	public record Login(String tokenType, String accessToken, long accessTokenExpiresIn, String refreshToken,
			long refreshTokenExpiresIn, PlayerSummary player, Bonus dailyBonus, Bonus comebackBonus) {
	}

	public record DailyBonuses(Bonus dailyBonus, Bonus comebackBonus) {
	}

	public record PlayerSummary(Long id, String username, PlayerRole role, boolean emailVerified) {
	}

	public record Bonus(boolean granted, int amount) {

		public static Bonus of(int amount) {
			return new Bonus(amount > 0, amount);
		}
	}
}
