package com.horizonglitch.wotv.deck;

import com.horizonglitch.wotv.card.CatalogResponses;
import java.time.Instant;
import java.util.List;

/** Response bodies of the deck routes. */
public final class DeckResponses {

	private DeckResponses() {
	}

	public record DeckCardView(CatalogResponses.CardView card, int quantity) {
	}

	/** {@code validationErrors} lists every rule the deck breaks now, empty for a VALID deck. */
	public record DeckView(Long id, String name, DeckStatus status, List<DeckCardView> cards, int totalCards,
			boolean isDefenseDeck, Instant updatedAt, List<DeckRules.Violation> validationErrors) {
	}

	public record ValidationView(DeckStatus status, int totalCards, List<DeckRules.Violation> validationErrors) {
	}
}
