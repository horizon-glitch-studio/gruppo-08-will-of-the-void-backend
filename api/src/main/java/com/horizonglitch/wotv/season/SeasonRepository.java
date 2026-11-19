package com.horizonglitch.wotv.season;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface SeasonRepository extends JpaRepository<Season, Integer> {

	@Query(value = "SELECT * FROM season WHERE reset_at IS NULL AND starts_at <= :now AND ends_at > :now "
			+ "ORDER BY starts_at DESC LIMIT 1", nativeQuery = true)
	Optional<Season> findCurrentAt(@Param("now") Instant now);

	@Query(value = "SELECT * FROM season WHERE reset_at IS NULL AND ends_at <= :now ORDER BY ends_at LIMIT 1 FOR UPDATE",
			nativeQuery = true)
	Optional<Season> lockNextEndedAt(@Param("now") Instant now);

	interface Standing {
		Long getPlayerId();

		Integer getTrophies();

		Integer getFinalRank();
	}

	@Query(value = """
			SELECT p.id AS "playerId", p.trophies AS "trophies",
			       rank() OVER (ORDER BY p.trophies DESC) AS "finalRank"
			FROM player p
			WHERE p.deleted_at IS NULL AND EXISTS (
			  SELECT 1 FROM battle b
			  WHERE b.status = 'PLAYED' AND b.mode = 'RANKED'
			    AND b.played_at >= :startsAt AND b.played_at < :endsAt
			    AND (b.challenger_id = p.id OR b.opponent_id = p.id)
			)
			ORDER BY p.trophies DESC, p.id
			""", nativeQuery = true)
	List<Standing> findRankedStandings(@Param("startsAt") Instant startsAt, @Param("endsAt") Instant endsAt);
}
