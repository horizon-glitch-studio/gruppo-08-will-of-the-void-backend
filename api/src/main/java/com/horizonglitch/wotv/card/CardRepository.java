package com.horizonglitch.wotv.card;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardRepository extends JpaRepository<Card, Long>, JpaSpecificationExecutor<Card> {

	Optional<Card> findByIdAndActiveTrue(Long id);

	@Query("select c.id from Card c where c.active = true and c.rarity.id = :rarityId order by c.id")
	List<Long> findActiveIdsByRarity(@Param("rarityId") Integer rarityId);
}
