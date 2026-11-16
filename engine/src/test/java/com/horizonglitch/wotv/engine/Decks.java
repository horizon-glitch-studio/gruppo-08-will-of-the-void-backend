package com.horizonglitch.wotv.engine;

import java.util.ArrayList;
import java.util.List;

/** Builders of cards and decks for the tests. */
final class Decks {

	static final MatchParameters OFFICIAL = new MatchParameters(30, 30, 20, 4, AdvantageTable.official(3));

	private Decks() {
	}

	static CardInput input(String faction, int attack, int defense) {
		return new CardInput(1, faction + " " + attack + "/" + defense, faction, "Captive", attack, defense);
	}

	/** A deck in fixed draw order: the position of every card is its index. */
	static List<CardSnapshot> ordered(CardInput... cards) {
		List<CardSnapshot> deck = new ArrayList<>();
		for (int i = 0; i < cards.length; i++) {
			deck.add(CardSnapshot.of(i, cards[i]));
		}
		return deck;
	}

	/** {@code size} copies of the same card, in fixed draw order. */
	static List<CardSnapshot> repeated(CardInput card, int size) {
		List<CardSnapshot> deck = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			deck.add(CardSnapshot.of(i, card));
		}
		return deck;
	}

	/** A 30-card deck of distinct cards spread over the six elemental factions, with varied ATK and DEF. */
	static List<CardInput> fullDeck(int firstCardId) {
		String[] factions = {"Lava", "Electro", "Radio", "Ice", "Metal", "Matte"};
		List<CardInput> deck = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			String faction = factions[i % factions.length];
			int attack = 3 + (i * 7) % 9;
			int defense = 12 - attack + (i % 4);
			deck.add(new CardInput(firstCardId + i, faction + " " + i, faction, "Captive", attack, defense));
		}
		return deck;
	}
}
