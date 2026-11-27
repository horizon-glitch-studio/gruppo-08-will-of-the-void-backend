package com.horizonglitch.wotv.collection;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.time.Instant;

public interface CollectionCardRepository
		extends JpaRepository<CollectionCard, CollectionCardId>, JpaSpecificationExecutor<CollectionCard> {

	@Query("select c from CollectionCard c where c.id.playerId = :playerId and c.id.cardId in :cardIds")
	List<CollectionCard> findOwned(@Param("playerId") Long playerId, @Param("cardIds") Collection<Long> cardIds);

	Optional<CollectionCard> findById_PlayerIdAndId_CardId(Long playerId, Long cardId);

	/** Adds permanent copies with one atomic statement, creating the row when the card is new. */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			INSERT INTO collection_card (player_id, card_id, quantity, acquired_at)
			VALUES (:playerId, :cardId, :quantity, :now)
			ON CONFLICT (player_id, card_id) DO UPDATE SET quantity = collection_card.quantity + EXCLUDED.quantity
			""", nativeQuery = true)
	int addPermanent(@Param("playerId") Long playerId, @Param("cardId") Long cardId, @Param("quantity") int quantity,
			@Param("now") Instant now);

	/** Removes copies only if enough are owned: returns 0 otherwise. */
	@Modifying(flushAutomatically = true)
	@Query(value = "UPDATE collection_card SET quantity = quantity - :quantity "
			+ "WHERE player_id = :playerId AND card_id = :cardId AND quantity > :quantity", nativeQuery = true)
	int removeSome(@Param("playerId") Long playerId, @Param("cardId") Long cardId, @Param("quantity") int quantity);

	/** Removes the row when the last copies leave. */
	@Modifying(flushAutomatically = true)
	@Query(value = "DELETE FROM collection_card WHERE player_id = :playerId AND card_id = :cardId "
			+ "AND quantity = :quantity", nativeQuery = true)
	int removeAll(@Param("playerId") Long playerId, @Param("cardId") Long cardId, @Param("quantity") int quantity);

	/**
	 * Locks the rows of the given cards of two players, always in the same order, so two trades of the same
	 * players cannot deadlock. Returns player_id, card_id and quantity of the rows found.
	 */
	@Query(value = """
			SELECT player_id, card_id, quantity FROM collection_card
			WHERE player_id IN (:firstPlayerId, :secondPlayerId) AND card_id IN (:cardIds)
			ORDER BY player_id, card_id
			FOR UPDATE
			""", nativeQuery = true)
	List<Object[]> lockRows(@Param("firstPlayerId") Long firstPlayerId, @Param("secondPlayerId") Long secondPlayerId,
			@Param("cardIds") Collection<Long> cardIds);

	/** Permanent copies owned: Wanderers (with an expiry) and archived cards do not count (TRADE-12). */
	@Query(value = "SELECT COALESCE(SUM(quantity), 0) FROM collection_card WHERE player_id = :playerId "
			+ "AND expires_at IS NULL", nativeQuery = true)
	int countPermanent(@Param("playerId") Long playerId);

	@Query("select c from CollectionCard c where c.expiresAt is not null and c.expiresAt <= :now")
	List<CollectionCard> findExpired(@Param("now") Instant now);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CollectionCard c where c.id = :id")
	Optional<CollectionCard> lockById(@Param("id") CollectionCardId id);
}
