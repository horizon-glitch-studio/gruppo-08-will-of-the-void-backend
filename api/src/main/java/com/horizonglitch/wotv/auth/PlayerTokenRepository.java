package com.horizonglitch.wotv.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerTokenRepository extends JpaRepository<PlayerToken, Long> {

	Optional<PlayerToken> findByTokenHash(String tokenHash);

	/** Returns 1 if this call used the token, 0 if it was already used. */
	@Modifying(flushAutomatically = true)
	@Query("update PlayerToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
	int markUsed(@Param("id") Long id, @Param("now") Instant now);

	@Modifying(flushAutomatically = true)
	@Query("update PlayerToken t set t.usedAt = :now where t.player.id = :playerId and t.type = :type and t.usedAt is null")
	int markAllUsed(@Param("playerId") Long playerId, @Param("type") TokenType type, @Param("now") Instant now);
}
