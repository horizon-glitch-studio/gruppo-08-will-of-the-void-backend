package com.horizonglitch.wotv.season;

import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Closes expired ranking seasons and creates the next one (RANK-07 to RANK-09). */
@Component
public class SeasonResetJob {

	private final SeasonService seasons;
	private final Clock clock;

	public SeasonResetJob(SeasonService seasons, Clock clock) {
		this.seasons = seasons;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.seasons.reset-check-interval}")
	public void closeExpiredSeasons() {
		while (seasons.closeNextEndedSeason(clock.instant())) {
			// Catch up one season per transaction if the service was offline across multiple season boundaries.
		}
	}
}
