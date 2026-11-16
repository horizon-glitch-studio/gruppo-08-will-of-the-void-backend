package com.horizonglitch.wotv.player;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvatarRepository extends JpaRepository<Avatar, Short> {

	Optional<Avatar> findByNameAndActiveTrue(String name);
}
