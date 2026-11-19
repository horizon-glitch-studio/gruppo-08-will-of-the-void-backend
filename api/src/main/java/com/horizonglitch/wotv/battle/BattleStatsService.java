package com.horizonglitch.wotv.battle;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only battle statistics shown on profiles and used by cross-feature rules such as ECO-10. */
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

	@Transactional(readOnly = true)
	public long distinctPlayedOpponents(long playerId) {
		return battles.countDistinctPlayedOpponents(playerId);
	}
}
