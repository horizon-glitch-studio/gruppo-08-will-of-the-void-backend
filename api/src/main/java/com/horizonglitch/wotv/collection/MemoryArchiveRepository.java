package com.horizonglitch.wotv.collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryArchiveRepository extends JpaRepository<MemoryArchive, Long> {

	Page<MemoryArchive> findByPlayer_Id(Long playerId, Pageable pageable);
}
