package com.horizonglitch.wotv.player;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, Long> {

	@Query("select p from Player p where lower(p.username) = lower(:username) and p.deletedAt is null")
	Optional<Player> findActiveByUsername(@Param("username") String username);

	@Query("select p from Player p where lower(p.email) = lower(:email) and p.deletedAt is null")
	Optional<Player> findActiveByEmail(@Param("email") String email);

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
}
