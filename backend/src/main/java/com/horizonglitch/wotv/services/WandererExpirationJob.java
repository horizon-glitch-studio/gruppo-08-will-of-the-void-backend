package com.horizonglitch.wotv.services;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WandererExpirationJob {

	private final JdbcTemplate jdbcTemplate;
	private final CollectionService collectionService;

	public WandererExpirationJob(JdbcTemplate jdbcTemplate, CollectionService collectionService) {
		this.jdbcTemplate = jdbcTemplate;
		this.collectionService = collectionService;
	}

	@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
	public void archiveExpiredWanderers() {
		List<Long> playerIds = jdbcTemplate.queryForList(
				"SELECT DISTINCT player_id FROM collection_card"
						+ " WHERE expires_at <= CURRENT_TIMESTAMP ORDER BY player_id",
				Long.class);
		playerIds.forEach((@NonNull Long playerId) -> collectionService.expireForPlayer(playerId));
	}
}