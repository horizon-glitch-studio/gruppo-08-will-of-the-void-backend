package com.horizonglitch.wotv.engine;

/**
 * A card in its post-shuffle position. Same fields as {@code cardSnapshot} in battle-viewer.schema.json: the
 * side and {@code deckPosition} identify the card in the events.
 */
public record CardSnapshot(int deckPosition, long cardId, String name, String faction, String rarity,
		int baseAttack, int baseDefense) {

	static CardSnapshot of(int deckPosition, CardInput card) {
		return new CardSnapshot(deckPosition, card.cardId(), card.name(), card.faction(), card.rarity(),
				card.attack(), card.defense());
	}
}
