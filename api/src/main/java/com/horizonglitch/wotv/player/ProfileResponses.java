package com.horizonglitch.wotv.player;

import com.horizonglitch.wotv.battle.BattleStats;
import com.horizonglitch.wotv.season.Orbit;
import java.time.Instant;

/** Response bodies of the profile routes. */
public final class ProfileResponses {

	private ProfileResponses() {
	}

	/** The private profile: only the player themselves sees it. */
	public record Me(
			Long id,
			String username,
			String email,
			boolean emailVerified,
			PlayerRole role,
			String avatar,
			int shardBalance,
			int trophies,
			Orbit league,
			Long defenseDeckId,
			Instant protectedUntil,
			String timezone,
			String inviteCode,
			String invitedBy,
			BattleStats stats,
			Instant createdAt) {
	}

	/** Never exposes email, Shards, role, decks, time zone or invite code. */
	public record Public(
			Long id,
			String username,
			String avatar,
			int trophies,
			Orbit league,
			BattleStats stats,
			Instant createdAt) {
	}

	public record Settings(boolean reduceMotion, boolean reduceTransparency, boolean showTextLabels) {

		static Settings of(PlayerSettings settings) {
			return new Settings(settings.isReduceMotion(), settings.isReduceTransparency(), settings.isShowTextLabels());
		}
	}
}
