package com.horizonglitch.wotv.pack;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PackTypeRepository extends JpaRepository<PackType, Integer> {

	List<PackType> findByActiveTrueOrderById();

	Optional<PackType> findByIdAndActiveTrue(Integer id);
}
