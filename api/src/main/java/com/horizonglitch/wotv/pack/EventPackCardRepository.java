package com.horizonglitch.wotv.pack;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventPackCardRepository extends JpaRepository<EventPackCard, EventPackCardId> {

	@Query("select e.card.id from EventPackCard e where e.packType.id = :packTypeId order by e.card.id")
	List<Long> findCardIds(@Param("packTypeId") Integer packTypeId);

	@Query("select e from EventPackCard e join fetch e.card where e.packType.id = :packTypeId order by e.card.id")
	List<EventPackCard> findWithCards(@Param("packTypeId") Integer packTypeId);
}
