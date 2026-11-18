package com.horizonglitch.wotv.battle;

import java.time.Instant;

/** Response bodies of the battle routes. The seed is never shown. */
public final class BattleResponses {

	private BattleResponses() {
	}

	public record PlayerRef(Long id, String username) {
	}

	/**
	 * A battle or a challenge. Outcome and Energy are set when the status is PLAYED, the trophy changes only in
	 * RANKED, {@code expiresAt} only for a challenge.
	 */
	public record BattleView(Long id, BattleMode mode, BattleStatus status, PlayerRef challenger, PlayerRef opponent,
			BattleOutcome outcome, Integer challengerEnergy, Integer opponentEnergy, Integer challengerTrophiesDelta,
			Integer opponentTrophiesDelta, Instant createdAt, Instant expiresAt, Instant playedAt, Long rematchOfId) {
	}

	public record SideResult(PlayerRef player, int energy, Integer trophiesDelta) {
	}

	/**
	 * The final result of a PLAYED battle. {@code endReason} is ENERGY_THRESHOLD or DECK_EXHAUSTED, and is null
	 * once the replay is no longer kept.
	 */
	public record ResultView(Long battleId, BattleMode mode, BattleOutcome outcome, String endReason,
			int winThreshold, SideResult challenger, SideResult opponent, Instant playedAt) {
	}
}
