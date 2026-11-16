package com.horizonglitch.wotv.deck;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** DECK-01 to DECK-05 without database. Factions 1 and 2 are elemental, 9 is inert. */
class DeckRulesTest {

	private static final int COMMON = 1;
	private static final int SOLDIER = 3;
	private static final int WANDERER = 6;

	@Test
	void thirtyCardsWithinEveryLimitAreValid() {
		DeckRules.Result result = DeckRules.check(validDeck(), 30, 15);
		assertThat(result.status()).isEqualTo(DeckStatus.VALID);
		assertThat(result.totalCards()).isEqualTo(30);
		assertThat(result.violations()).isEmpty();
	}

	@Test
	void fewerCardsAreIncompleteMoreAreInvalid() {
		List<DeckRules.Entry> deck = validDeck();
		deck.remove(0);
		DeckRules.Result fewer = DeckRules.check(deck, 30, 15);
		assertThat(fewer.status()).isEqualTo(DeckStatus.INCOMPLETE);
		assertThat(fewer.violations()).extracting(DeckRules.Violation::code).containsExactly(DeckRules.DECK_SIZE);

		deck = validDeck();
		deck.add(common(100, 2, 1));
		assertThat(DeckRules.check(deck, 30, 15).status()).isEqualTo(DeckStatus.INVALID);
	}

	@Test
	void tooFewCardsAndABrokenRuleAreInvalid() {
		List<DeckRules.Entry> deck = List.of(new DeckRules.Entry(1, 4, 1, false, COMMON, 3, null, 4, true));
		DeckRules.Result result = DeckRules.check(deck, 30, 15);
		assertThat(result.status()).isEqualTo(DeckStatus.INVALID);
		assertThat(result.violations()).extracting(DeckRules.Violation::code)
				.containsExactlyInAnyOrder(DeckRules.TOO_MANY_COPIES, DeckRules.DECK_SIZE);
	}

	@Test
	void countsEachLimit() {
		List<DeckRules.Entry> deck = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			deck.add(new DeckRules.Entry(10 + i, 2, 1, false, SOLDIER, 2, 8, 2, true));
		}
		deck.add(new DeckRules.Entry(20, 3, 1, false, COMMON, 3, null, 3, true));
		deck.add(new DeckRules.Entry(21, 3, 1, false, COMMON, 3, null, 1, true));
		deck.add(new DeckRules.Entry(22, 1, 9, true, WANDERER, 1, 1, 1, false));

		DeckRules.Result result = DeckRules.check(deck, 30, 15);
		assertThat(result.violations()).extracting(DeckRules.Violation::code).containsExactlyInAnyOrder(
				DeckRules.RARITY_LIMIT, DeckRules.FACTION_LIMIT, DeckRules.NOT_ENOUGH_COPIES,
				DeckRules.CARD_NOT_PLAYABLE, DeckRules.DECK_SIZE);
		DeckRules.Violation faction = result.violations().stream()
				.filter(v -> v.code().equals(DeckRules.FACTION_LIMIT)).findFirst().orElseThrow();
		assertThat(faction.factionId()).isEqualTo(1);
		assertThat(faction.actual()).isEqualTo(10 + 3 + 3);
	}

	@Test
	void inertFactionsDoNotCountForTheFactionLimit() {
		List<DeckRules.Entry> deck = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			deck.add(common(i, 1, 3));
		}
		deck.add(new DeckRules.Entry(50, 1, 9, true, WANDERER, 1, 1, 1, true));
		for (int i = 0; i < 5; i++) {
			deck.add(common(60 + i, 2, i < 4 ? 3 : 2));
		}
		assertThat(DeckRules.check(deck, 30, 15).violations()).isEmpty();
	}

	/** 5 cards x 3 copies of faction 1 and 5 x 3 of faction 2. */
	private static List<DeckRules.Entry> validDeck() {
		List<DeckRules.Entry> deck = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			deck.add(common(i, 1, 3));
			deck.add(common(10 + i, 2, 3));
		}
		return deck;
	}

	private static DeckRules.Entry common(long cardId, int factionId, int quantity) {
		return new DeckRules.Entry(cardId, quantity, factionId, false, COMMON, 3, null, quantity, true);
	}
}
