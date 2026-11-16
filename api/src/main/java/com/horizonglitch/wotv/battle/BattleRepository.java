package com.horizonglitch.wotv.battle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BattleRepository extends JpaRepository<Battle, Long> {

	/** Counts of the PLAYED battles of a player, as challenger or opponent, in every mode. */
	@Query(value = """
			SELECT count(*) AS played,
				   count(*) FILTER (WHERE (challenger_id = :playerId AND outcome = 'CHALLENGER_WIN')
									   OR (opponent_id = :playerId AND outcome = 'OPPONENT_WIN')) AS wins,
				   count(*) FILTER (WHERE outcome = 'DRAW') AS draws
			FROM battle
			WHERE status = 'PLAYED' AND (challenger_id = :playerId OR opponent_id = :playerId)
			""", nativeQuery = true)
	BattleCounts countPlayed(@Param("playerId") Long playerId);

	interface BattleCounts {
		long getPlayed();

		long getWins();

		long getDraws();
	}
}
