package com.horizonglitch.wotv.season;

import com.horizonglitch.wotv.player.Player;
import com.horizonglitch.wotv.player.PlayerRepository;
import com.horizonglitch.wotv.shard.ShardService;
import com.horizonglitch.wotv.config.AppProperties;
import com.horizonglitch.wotv.common.PageResponse;
import com.horizonglitch.wotv.common.Paging;
import com.horizonglitch.wotv.common.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to the active season used by ranking rewards (RANK-07, RANK-11). */
@Service
public class SeasonService {

	private final SeasonRepository seasons;
	private final SeasonResultRepository results;
	private final PlayerRepository players;
	private final ShardService shards;
	private final AppProperties.Seasons settings;

	public SeasonService(SeasonRepository seasons, SeasonResultRepository results, PlayerRepository players,
			ShardService shards, AppProperties properties) {
		this.seasons = seasons;
		this.results = results;
		this.players = players;
		this.shards = shards;
		this.settings = properties.seasons();
	}

	@Transactional(readOnly = true)
	public Optional<Season> currentAt(Instant instant) {
		return seasons.findCurrentAt(instant);
	}

	/** RANK-12 to RANK-14: only active-season Ranked participants are listed. */
	@Transactional(readOnly = true)
	public PageResponse<LeaderboardEntry> leaderboard(Orbit orbit, Integer page, Integer size, Instant now) {
		Season season = seasons.findCurrentAt(now).orElseThrow(() -> new ApiException(
				org.springframework.http.HttpStatus.NOT_FOUND, "SEASON_NOT_FOUND", "There is no current season."));
		Integer minTrophies = orbit == null ? null : orbit.minTrophies();
		Integer maxTrophies = orbit == null || orbit == Orbit.ORBIT_4
				? null : Orbit.values()[orbit.ordinal() + 1].minTrophies() - 1;
		return PageResponse.of(seasons.findLeaderboard(season.getStartsAt(), season.getEndsAt(), minTrophies,
				maxTrophies, Paging.of(page, size, Sort.unsorted())), entry -> new LeaderboardEntry(entry.getPosition(),
				entry.getPlayerId(), entry.getUsername(), entry.getAvatar(), entry.getTrophies(),
				Orbit.fromTrophies(entry.getTrophies())));
	}

	public record LeaderboardEntry(long position, long id, String username, String avatar, int trophies, Orbit orbit) {}
	public record CurrentSeason(int id, Instant startsAt, Instant endsAt) {}

	@Transactional(readOnly = true)
	public CurrentSeason current(Instant now) {
		Season season = seasons.findCurrentAt(now).orElseThrow(() -> new ApiException(
				org.springframework.http.HttpStatus.NOT_FOUND, "SEASON_NOT_FOUND", "There is no current season."));
		return new CurrentSeason(season.getId(), season.getStartsAt(), season.getEndsAt());
	}

	/**
	 * RANK-08: the trophies above {@code threshold} are halved, rounded to the nearest whole number with a half
	 * rounded up (1201 becomes 1101). Summoners at the threshold or below keep their trophies. The same formula is
	 * applied in SQL by {@link PlayerRepository#resetTrophiesAbove(int)}.
	 */
	public static int trophiesAfterReset(int trophies, int threshold) {
		return trophies > threshold ? threshold + (trophies - threshold + 1) / 2 : trophies;
	}

	/** RANK-08, RANK-09: rewards eligible Summoners, records their final standings, resets Trophies and rolls over. */
	@Transactional
	public boolean closeNextEndedSeason(Instant now) {
		Optional<Season> pending = seasons.lockNextEndedAt(now);
		if (pending.isEmpty()) {
			return false;
		}

		Season season = pending.get();
		List<SeasonRepository.Standing> standings = seasons.findRankedStandings(season.getStartsAt(), season.getEndsAt());
		var playerIds = standings.stream().map(SeasonRepository.Standing::getPlayerId).toList();
		Map<Long, Player> playersById = players.findAllById(playerIds).stream()
				.collect(java.util.stream.Collectors.toMap(Player::getId, player -> player));
		var seasonResults = new ArrayList<SeasonResult>(standings.size());
		for (SeasonRepository.Standing standing : standings) {
			Player player = playersById.get(standing.getPlayerId());
			int trophiesBefore = standing.getTrophies();
			int trophiesAfter = trophiesAfterReset(trophiesBefore, settings.resetThreshold());
			SeasonResult result = new SeasonResult();
			result.setSeason(season);
			result.setPlayer(player);
			result.setTrophiesBefore(trophiesBefore);
			result.setTrophiesAfter(trophiesAfter);
			result.setFinalRank(standing.getFinalRank());
			seasonResults.add(result);
			shards.grantSeasonReward(player, season, Orbit.fromTrophies(trophiesBefore));
		}
		results.saveAll(seasonResults);
		players.resetTrophiesAbove(settings.resetThreshold());

		season.setResetAt(now);
		seasons.save(season);
		Season nextSeason = new Season();
		nextSeason.setStartsAt(season.getEndsAt());
		nextSeason.setEndsAt(season.getEndsAt().plusSeconds(settings.lengthWeeks() * 7L * 24 * 60 * 60));
		seasons.save(nextSeason);
		return true;
	}
}
