package com.horizonglitch.wotv.matchmaking;

import com.horizonglitch.wotv.battle.BattleMode;
import jakarta.validation.constraints.NotNull;

/** Request bodies of the matchmaking routes. {@code mode} is QUICK or RANKED. */
public final class MatchmakingRequests {

	private MatchmakingRequests() {
	}

	public record Refresh(@NotNull(message = "FIELD_REQUIRED") BattleMode mode) {
	}

	/** The attack on an opponent of the list, with one of the player's decks. */
	public record Start(@NotNull(message = "FIELD_REQUIRED") BattleMode mode,
			@NotNull(message = "FIELD_REQUIRED") Long deckId) {
	}
}
