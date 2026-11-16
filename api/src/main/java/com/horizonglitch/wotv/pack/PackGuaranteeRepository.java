package com.horizonglitch.wotv.pack;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PackGuaranteeRepository extends JpaRepository<PackGuarantee, Long> {

	/** Locks the counters: two packs opened at the same time are drawn one after the other. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from PackGuarantee g where g.playerId = :playerId")
	Optional<PackGuarantee> lockByPlayerId(@Param("playerId") Long playerId);
}
