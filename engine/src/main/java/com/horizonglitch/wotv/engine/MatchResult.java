package com.horizonglitch.wotv.engine;

import java.util.List;

/**
 * The outcome of a match. {@code shuffledDeckA} and {@code shuffledDeckB} are the decks in their actual draw
 * order (the {@code deckSnapshot.cards} of battle-viewer.schema.json); {@code events} is the complete stream,
 * ending with a {@link MatchEvent.BattleFinished}.
 */
public record MatchResult(Winner winner, EndReason endReason, EnergyBySide finalEnergy,
		List<CardSnapshot> shuffledDeckA, List<CardSnapshot> shuffledDeckB, List<MatchEvent> events) {

	public List<CardSnapshot> shuffledDeck(Side side) {
		return side == Side.A ? shuffledDeckA : shuffledDeckB;
	}
}
