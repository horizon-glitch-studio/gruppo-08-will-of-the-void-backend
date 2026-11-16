package com.horizonglitch.wotv.collection;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectionCardRepository
		extends JpaRepository<CollectionCard, CollectionCardId>, JpaSpecificationExecutor<CollectionCard> {

	@Query("select c from CollectionCard c where c.id.playerId = :playerId and c.id.cardId in :cardIds")
	List<CollectionCard> findOwned(@Param("playerId") Long playerId, @Param("cardIds") Collection<Long> cardIds);

	Optional<CollectionCard> findById_PlayerIdAndId_CardId(Long playerId, Long cardId);
}
