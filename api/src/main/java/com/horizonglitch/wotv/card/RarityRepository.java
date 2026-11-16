package com.horizonglitch.wotv.card;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RarityRepository extends JpaRepository<Rarity, Integer> {

	Optional<Rarity> findByName(String name);
}
