package com.horizonglitch.wotv.engine;

import java.util.List;

/**
 * Everything the engine needs to play a match. Both decks are valid (the API checks them with DeckRules) and in
 * the order the Summoner saved them: the engine shuffles them with {@code seed} (MATCH-02).
 */
public record MatchRequest(List<CardInput> deckA, List<CardInput> deckB, MatchMode mode, long seed,
		MatchParameters parameters) {

	public MatchRequest {
		if (deckA == null || deckB == null || deckA.isEmpty() || deckB.isEmpty()) {
			throw new IllegalArgumentException("Both decks must have at least one card");
		}
		if (mode == null || parameters == null) {
			throw new IllegalArgumentException("mode and parameters are required");
		}
		deckA = List.copyOf(deckA);
		deckB = List.copyOf(deckB);
	}
}
