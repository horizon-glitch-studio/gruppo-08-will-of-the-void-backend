package com.horizonglitch.wotv.battle;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BattleRepository extends JpaRepository<Battle, Long>, JpaSpecificationExecutor<Battle> {

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

	/** Locks the battle, so accept, reject, cancel and expiry of the same challenge happen one at a time. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Battle b where b.id = :id")
	Optional<Battle> lockById(@Param("id") Long id);

	/** The two players of a battle, read without loading it. */
	interface Parties {
		Long getChallengerId();

		Long getOpponentId();
	}

	@Query("select b.challenger.id as challengerId, b.opponent.id as opponentId from Battle b where b.id = :id")
	Optional<Parties> findParties(@Param("id") Long id);

	/** The pending challenge between two players, in either direction (uq_battle_pending_pair, MODE-07). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select b from Battle b where b.status = com.horizonglitch.wotv.battle.BattleStatus.PENDING
			and ((b.challenger.id = :a and b.opponent.id = :b) or (b.challenger.id = :b and b.opponent.id = :a))
			""")
	Optional<Battle> lockPendingBetween(@Param("a") Long a, @Param("b") Long b);

	@Query("select b.id from Battle b where b.status = com.horizonglitch.wotv.battle.BattleStatus.PENDING "
			+ "and b.expiresAt <= :now order by b.id")
	List<Long> findExpiredPendingIds(@Param("now") Instant now);

	@Query("select b from Battle b where b.status = com.horizonglitch.wotv.battle.BattleStatus.PENDING "
			+ "and (b.challenger.id = :playerId or b.opponent.id = :playerId)")
	List<Battle> findPendingOf(@Param("playerId") Long playerId);

	/**
	 * Attacks of a player on the same opponent in one mode since {@code since} (MODE-16, MODE-17). Rematches are
	 * not attacks from a list and are not counted (MODE-23).
	 */
	@Query(value = """
			SELECT count(*) FROM battle
			WHERE challenger_id = :challengerId AND opponent_id = :opponentId AND mode = :mode
			  AND status = 'PLAYED' AND rematch_of_id IS NULL AND played_at > :since
			""", nativeQuery = true)
	long countAttacks(@Param("challengerId") Long challengerId, @Param("opponentId") Long opponentId,
			@Param("mode") String mode, @Param("since") Instant since);

	/**
	 * Ranked attacks received since {@code since} that count for the next shield (MODE-19): the ones received
	 * before the end of the last shield were already counted for it.
	 */
	@Query(value = """
			SELECT count(*) FROM battle b JOIN player p ON p.id = b.opponent_id
			WHERE b.opponent_id = :playerId AND b.mode = 'RANKED' AND b.status = 'PLAYED' AND b.played_at > :since
			  AND (p.protected_until IS NULL OR b.played_at > p.protected_until)
			""", nativeQuery = true)
	long countRankedAttacksReceived(@Param("playerId") Long playerId, @Param("since") Instant since);
}
