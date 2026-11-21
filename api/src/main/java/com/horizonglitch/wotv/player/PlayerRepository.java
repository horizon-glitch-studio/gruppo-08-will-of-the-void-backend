package com.horizonglitch.wotv.player;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, Long> {

	@Query("select p from Player p where lower(p.username) = lower(:username) and p.deletedAt is null")
	Optional<Player> findActiveByUsername(@Param("username") String username);

	@Query("select p from Player p where lower(p.email) = lower(:email) and p.deletedAt is null")
	Optional<Player> findActiveByEmail(@Param("email") String email);

	/** The id only, so the row can then be locked and read fresh. */
	@Query("select p.id from Player p where lower(p.username) = lower(:username) and p.deletedAt is null")
	Optional<Long> findActiveIdByUsername(@Param("username") String username);

	Optional<Player> findByInviteCodeAndDeletedAtIsNull(String inviteCode);

	Optional<Player> findByIdAndDeletedAtIsNull(Long id);

	@Query("select count(p) > 0 from Player p where lower(p.username) = lower(:username)")
	boolean existsByUsernameIgnoringCase(@Param("username") String username);

	@Query("select count(p) > 0 from Player p where lower(p.email) = lower(:email)")
	boolean existsByEmailIgnoringCase(@Param("email") String email);

	boolean existsByInviteCode(String inviteCode);

	/** Locks the row until the end of the transaction (SELECT ... FOR UPDATE). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Player p where p.id = :id and p.deletedAt is null")
	Optional<Player> lockById(@Param("id") Long id);

	/** The current trophies, read from the database: the entity may hold an older value. */
	@Query(value = "SELECT trophies FROM player WHERE id = :id", nativeQuery = true)
	int trophiesOf(@Param("id") Long id);

	/** Atomic change of the trophies, never below 0 (RANK-05). The trophies are never written through the entity. */
	@Modifying(flushAutomatically = true)
	@Query(value = "UPDATE player SET trophies = GREATEST(trophies + :delta, 0) WHERE id = :id", nativeQuery = true)
	int addTrophies(@Param("id") Long id, @Param("delta") int delta);

	/**
	 * Applies RANK-08 to every Summoner in one database statement, with a half rounded up: the same formula as
	 * {@code SeasonService.trophiesAfterReset}.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "UPDATE player SET trophies = :threshold + (trophies - :threshold + 1) / 2 "
			+ "WHERE trophies > :threshold", nativeQuery = true)
	int resetTrophiesAbove(@Param("threshold") int threshold);

	@Query(value = "SELECT protected_until IS NOT NULL AND protected_until > :now FROM player WHERE id = :id",
			nativeQuery = true)
	boolean isShielded(@Param("id") Long id, @Param("now") Instant now);

	/** Starts the shield (MODE-19). */
	@Modifying(flushAutomatically = true)
	@Query(value = "UPDATE player SET protected_until = :until WHERE id = :id", nativeQuery = true)
	int shieldUntil(@Param("id") Long id, @Param("until") Instant until);

	/**
	 * Ends an active shield at once (MODE-21). The column keeps the instant the shield ended, so the attacks
	 * received before it are not counted again for the next shield.
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = "UPDATE player SET protected_until = :now WHERE id = :id AND protected_until > :now",
			nativeQuery = true)
	int endShield(@Param("id") Long id, @Param("now") Instant now);

	@Query(value = "SELECT coalesce(max(trophies), 0) FROM player WHERE deleted_at IS NULL", nativeQuery = true)
	int maxTrophies();

	/** A player offered in an opponent list, with the trophies they have now. */
	interface Candidate {
		Long getId();

		Integer getTrophies();
	}

	/**
	 * Random Ranked opponents (MODE-12, MODE-15): within the trophy range, with a VALID defense deck and no
	 * active shield, never the player themselves.
	 */
	@Query(value = """
			SELECT p.id AS id, p.trophies AS trophies
			FROM player p JOIN deck d ON d.id = p.defense_deck_id
			WHERE p.deleted_at IS NULL AND p.id <> :playerId AND d.status = 'VALID'
			  AND (p.protected_until IS NULL OR p.protected_until <= :now)
			  AND p.trophies BETWEEN :minTrophies AND :maxTrophies
			ORDER BY random() LIMIT :size
			""", nativeQuery = true)
	List<Candidate> findRankedCandidates(@Param("playerId") Long playerId, @Param("now") Instant now,
			@Param("minTrophies") int minTrophies, @Param("maxTrophies") int maxTrophies, @Param("size") int size);

	/** Random Quick opponents (MODE-14): active since {@code activeSince}, with or without a defense deck. */
	@Query(value = """
			SELECT p.id AS id, p.trophies AS trophies
			FROM player p
			WHERE p.deleted_at IS NULL AND p.id <> :playerId AND p.last_daily_bonus_at >= :activeSince
			ORDER BY random() LIMIT :size
			""", nativeQuery = true)
	List<Candidate> findQuickCandidates(@Param("playerId") Long playerId, @Param("activeSince") Instant activeSince,
			@Param("size") int size);
}
