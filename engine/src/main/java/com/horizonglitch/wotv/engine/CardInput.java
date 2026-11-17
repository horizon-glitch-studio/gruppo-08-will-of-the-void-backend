package com.horizonglitch.wotv.engine;

/**
 * A card of a deck as the engine receives it: one entry per copy. {@code faction} and {@code rarity} are the
 * catalog names ("Lava", "Noble Gas", "Captive"...). ATK and DEF are at least 1: the engine does not validate
 * decks, the API does.
 */
public record CardInput(long cardId, String name, String faction, String rarity, int attack, int defense) {

	public CardInput {
		if (name == null || faction == null || rarity == null) {
			throw new IllegalArgumentException("name, faction and rarity are required");
		}
		if (attack < 1 || defense < 1) {
			throw new IllegalArgumentException("ATK and DEF must be at least 1");
		}
	}
}
