package com.horizonglitch.wotv.card;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StarterDeckCardRepository extends JpaRepository<StarterDeckCard, Long> {

	@Query("select s from StarterDeckCard s join fetch s.card order by s.cardId")
	List<StarterDeckCard> findAllWithCards();
}
