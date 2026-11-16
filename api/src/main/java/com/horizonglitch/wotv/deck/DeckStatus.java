package com.horizonglitch.wotv.deck;

/** Validity of a {@link Deck}, recomputed when its cards or the catalog change. Stored as text; the values match the CHECK constraint of the column. */
public enum DeckStatus {
	INCOMPLETE, VALID, INVALID
}
