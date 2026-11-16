package com.horizonglitch.wotv.shard;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShardTransactionRepository extends JpaRepository<ShardTransaction, Long> {

	Page<ShardTransaction> findByPlayer_Id(Long playerId, Pageable pageable);

	@Query(value = "SELECT shard_balance FROM player WHERE id = :playerId", nativeQuery = true)
	int balanceOf(@Param("playerId") Long playerId);

	boolean existsByPlayer_IdAndReasonAndCreatedAtAfter(Long playerId, ShardReason reason, Instant after);

	/**
	 * Atomic change of the balance, never below 0: returns 1 if applied, 0 if the balance was too low. The
	 * balance is never written through the entity (see Player).
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = "UPDATE player SET shard_balance = shard_balance + :amount "
			+ "WHERE id = :playerId AND shard_balance + :amount >= 0", nativeQuery = true)
	int addToBalance(@Param("playerId") Long playerId, @Param("amount") int amount);
}
