package com.horizonglitch.wotv.season;

import com.horizonglitch.wotv.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Leaderboard and current-season routes (RANK-12 to RANK-14). */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Ranking")
@SecurityRequirement(name = "bearer")
public class SeasonController {

	private final SeasonService seasons;
	private final Clock clock;

	public SeasonController(SeasonService seasons, Clock clock) {
		this.seasons = seasons;
		this.clock = clock;
	}

	@Operation(summary = "Current-season leaderboard, optionally filtered by Orbit")
	@GetMapping("/leaderboard")
	public PageResponse<SeasonService.LeaderboardEntry> leaderboard(
			@RequestParam(required = false) Orbit orbit,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return seasons.leaderboard(orbit, page, size, clock.instant());
	}

	@Operation(summary = "Current Ranked season")
	@GetMapping("/seasons/current")
	public SeasonService.CurrentSeason current() {
		return seasons.current(clock.instant());
	}
}
