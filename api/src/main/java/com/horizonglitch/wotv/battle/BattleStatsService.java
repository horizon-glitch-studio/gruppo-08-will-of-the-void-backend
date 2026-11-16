package com.horizonglitch.wotv.battle;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Statistics shown on the profiles, computed from the battle table. */
@Service
public class BattleStatsService {

	private final BattleRepository battles;

	public BattleStatsService(BattleRepository battles) {
		this.battles = battles;
	}

	@Transactional(readOnly = true)
	public BattleStats statsOf(long playerId) {
		BattleRepository.BattleCounts counts = battles.countPlayed(playerId);
		return BattleStats.of(counts.getPlayed(), counts.getWins(), counts.getDraws());
	}
}
