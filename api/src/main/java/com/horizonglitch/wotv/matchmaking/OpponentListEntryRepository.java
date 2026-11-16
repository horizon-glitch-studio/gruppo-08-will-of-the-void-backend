package com.horizonglitch.wotv.matchmaking;

import com.horizonglitch.wotv.battle.BattleMode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpponentListEntryRepository extends JpaRepository<OpponentListEntry, OpponentListEntryId> {

	/** The entry of an opponent in the current list of a player for one mode. */
	@Query("select e from OpponentListEntry e where e.id.playerId = :playerId and e.id.mode = :mode "
			+ "and e.opponent.id = :opponentId")
	Optional<OpponentListEntry> findEntry(@Param("playerId") Long playerId, @Param("mode") BattleMode mode,
			@Param("opponentId") Long opponentId);

	@Query("select count(e) > 0 from OpponentListEntry e where e.id.playerId = :playerId and e.id.mode = :mode "
			+ "and e.opponent.id = :opponentId")
	boolean isInList(@Param("playerId") Long playerId, @Param("mode") BattleMode mode,
			@Param("opponentId") Long opponentId);
}
