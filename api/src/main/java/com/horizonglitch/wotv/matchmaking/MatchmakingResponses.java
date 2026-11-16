package com.horizonglitch.wotv.matchmaking;

import com.horizonglitch.wotv.battle.BattleMode;
import com.horizonglitch.wotv.battle.BattleResponses;
import com.horizonglitch.wotv.season.Orbit;
import java.time.Instant;
import java.util.List;

/** Response bodies of the matchmaking routes. */
public final class MatchmakingResponses {

	private MatchmakingResponses() {
	}

	/**
	 * An opponent of the list. {@code trophiesAtGeneration} and {@code orbit} are for display only; {@code battleId}
	 * is the battle played against them, and {@code attacked} is true once it is set (MODE-10).
	 */
	public record OpponentView(int position, BattleResponses.PlayerRef opponent, int trophiesAtGeneration, Orbit orbit,
			boolean attacked, Long battleId) {
	}

	/** {@code refreshAvailableAt} is when the list can be refreshed again (MODE-11). */
	public record OpponentListView(BattleMode mode, Instant generatedAt, Instant refreshAvailableAt,
			List<OpponentView> opponents) {
	}
}
