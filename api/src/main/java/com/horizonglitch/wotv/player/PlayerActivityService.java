package com.horizonglitch.wotv.player;

import com.horizonglitch.wotv.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records authenticated game activity without writing on every request. */
@Service
public class PlayerActivityService {

	private static final Duration WRITE_INTERVAL = Duration.ofHours(1);

	private final PlayerRepository players;
	private final Clock clock;
	private final int comebackAbsenceDays;

	public PlayerActivityService(PlayerRepository players, Clock clock, AppProperties properties) {
		this.players = players;
		this.clock = clock;
		this.comebackAbsenceDays = properties.economy().comebackAbsenceDays();
	}

	@Transactional
	public void record(long playerId) {
		Instant now = clock.instant();
		players.touchLastActiveAt(playerId, now, now.minus(WRITE_INTERVAL),
				now.minus(Duration.ofDays(comebackAbsenceDays)));
	}

	@Transactional
	public void record(Player player) {
		Instant now = clock.instant();
		Instant previousActivity = player.getLastActiveAt();
		if (players.touchLastActiveAt(player.getId(), now, now.minus(WRITE_INTERVAL),
				now.minus(Duration.ofDays(comebackAbsenceDays))) == 1) {
			if (previousActivity != null
					&& !previousActivity.isAfter(now.minus(Duration.ofDays(comebackAbsenceDays)))) {
				player.setComebackEligible(true);
			}
			player.setLastActiveAt(now);
		}
	}
}
