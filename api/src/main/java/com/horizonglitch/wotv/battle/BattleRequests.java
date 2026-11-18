package com.horizonglitch.wotv.battle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request bodies of the battle routes. */
public final class BattleRequests {

	private BattleRequests() {
	}

	/** A match against a friend, with one of the player's decks. */
	public record Friend(@NotBlank(message = "FIELD_REQUIRED") String opponentUsername,
			@NotNull(message = "FIELD_REQUIRED") Long deckId) {
	}

	/** The deck chosen to accept a challenge or to ask for a rematch. */
	public record DeckChoice(@NotNull(message = "FIELD_REQUIRED") Long deckId) {
	}
}
