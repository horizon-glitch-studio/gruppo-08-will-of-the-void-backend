package com.horizonglitch.wotv.battle;

/** Result of a PLAYED {@link Battle}. Stored as text; the values match the CHECK constraint of the column. */
public enum BattleOutcome {
	CHALLENGER_WIN, OPPONENT_WIN, DRAW
}
