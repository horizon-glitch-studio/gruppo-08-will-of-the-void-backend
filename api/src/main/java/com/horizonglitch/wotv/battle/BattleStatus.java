package com.horizonglitch.wotv.battle;

/** State of a {@link Battle}: PENDING is a challenge request waiting for the opponent. Stored as text; the values match the CHECK constraint of the column. */
public enum BattleStatus {
	PENDING, REJECTED, EXPIRED, CANCELLED, PLAYED
}
