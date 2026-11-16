package com.horizonglitch.wotv.battle;

/** Mode of a {@link Battle}. Opponent lists use only QUICK and RANKED. Stored as text; the values match the CHECK constraint of the column. */
public enum BattleMode {
	FRIEND, QUICK, RANKED
}
