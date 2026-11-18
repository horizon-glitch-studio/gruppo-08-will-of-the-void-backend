package com.horizonglitch.wotv.battle;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BattleLogRepository extends JpaRepository<BattleLog, Long> {

	/** Deletes the logs of the battles played before {@code cutoff}: the result stays in the history (MATCH-18). */
	@Modifying
	@Query(value = "DELETE FROM battle_log l USING battle b WHERE b.id = l.battle_id AND b.played_at < :cutoff",
			nativeQuery = true)
	int deleteOfBattlesPlayedBefore(@Param("cutoff") Instant cutoff);
}
