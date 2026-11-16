package com.horizonglitch.wotv.pack;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PackOpeningRepository extends JpaRepository<PackOpening, Long> {

	Page<PackOpening> findByPlayer_Id(Long playerId, Pageable pageable);

	Optional<PackOpening> findByIdAndPlayer_Id(Long id, Long playerId);
}
