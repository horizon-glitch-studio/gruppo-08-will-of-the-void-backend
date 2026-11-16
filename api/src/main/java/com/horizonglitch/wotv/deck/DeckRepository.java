package com.horizonglitch.wotv.deck;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeckRepository extends JpaRepository<Deck, Long> {

	List<Deck> findByPlayer_IdOrderById(Long playerId);

	Optional<Deck> findByIdAndPlayer_Id(Long id, Long playerId);

	long countByPlayer_Id(Long playerId);

	boolean existsByPlayer_IdAndName(Long playerId, String name);

	boolean existsByPlayer_IdAndNameAndIdNot(Long playerId, String name, Long id);
}
