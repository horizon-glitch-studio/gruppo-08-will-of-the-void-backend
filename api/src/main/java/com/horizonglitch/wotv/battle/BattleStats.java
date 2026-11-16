package com.horizonglitch.wotv.battle;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Results of the PLAYED battles of a player. A draw counts as played, not as a win. */
public record BattleStats(long matchesPlayed, long wins, long losses, long draws, double winRate) {

	/** {@code winRate} = wins / matches played * 100, rounded to one decimal; 0 without battles. */
	public static BattleStats of(long played, long wins, long draws) {
		double winRate = played == 0 ? 0
				: BigDecimal.valueOf(wins * 100.0 / played).setScale(1, RoundingMode.HALF_UP).doubleValue();
		return new BattleStats(played, wins, played - wins - draws, draws, winRate);
	}
}
