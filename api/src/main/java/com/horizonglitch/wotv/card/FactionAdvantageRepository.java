package com.horizonglitch.wotv.card;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FactionAdvantageRepository extends JpaRepository<FactionAdvantage, FactionAdvantageId> {

	/** Every advantage with both factions loaded: the table the engine needs. */
	@Query("select a from FactionAdvantage a join fetch a.dominantFaction join fetch a.dominatedFaction")
	List<FactionAdvantage> findAllWithFactions();
}
