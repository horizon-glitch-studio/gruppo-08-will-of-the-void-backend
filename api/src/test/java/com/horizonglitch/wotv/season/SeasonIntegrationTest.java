package com.horizonglitch.wotv.season;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.horizonglitch.wotv.battle.BattleFixtures;
import com.horizonglitch.wotv.battle.BattleFixtures.Player;
import com.horizonglitch.wotv.config.AppProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The end of a season (RANK-08, RANK-09) against the real schema. The test closes a season of its own that ended
 * yesterday; the next season it creates ends in the future, so the scheduled job never resets trophies while
 * other tests run. The reset changes the trophies of every Summoner above 1000, so the assertions look only at
 * the players of this test.
 */
@SpringBootTest
class SeasonIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private SeasonService seasons;

	@Autowired
	private AppProperties properties;

	@Autowired
	private Clock clock;

	private BattleFixtures fx;

	@BeforeEach
	void setUp() {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		fx = new BattleFixtures(mvc, jdbc);
	}

	@Test
	void theEndOfASeasonRewardsRankedSummonersAndResetsTrophies() throws Exception {
		Player alice = fx.newPlayer();
		Player bob = fx.newPlayer();
		Player carol = fx.newPlayer();
		fx.setTrophies(alice, 1201);
		fx.setTrophies(bob, 300);
		fx.setTrophies(carol, 1500);
		int seasonId = endedSeason();
		rankedBattle(alice, bob, seasonId);
		int aliceShards = shards(alice);
		int bobShards = shards(bob);
		int carolShards = shards(carol);

		closeEndedSeasons();

		// RANK-08: halved above 1000, a half rounded up; everyone above the threshold, Ranked or not
		assertThat(fx.trophies(alice)).isEqualTo(1101);
		assertThat(fx.trophies(bob)).isEqualTo(300);
		assertThat(fx.trophies(carol)).isEqualTo(1250);

		// one result per Summoner with a Ranked battle in the season, ranked by trophies before the reset
		assertThat(result(seasonId, alice, "trophies_before")).isEqualTo(1201);
		assertThat(result(seasonId, alice, "trophies_after")).isEqualTo(1101);
		assertThat(result(seasonId, bob, "trophies_after")).isEqualTo(300);
		assertThat(result(seasonId, alice, "final_rank")).isLessThan(result(seasonId, bob, "final_rank"));
		assertThat(jdbc.queryForObject("select count(*) from season_result where season_id = ? and player_id = ?",
				Integer.class, seasonId, carol.id())).isZero();

		// RANK-09: the reward follows the Orbit before the reset
		AppProperties.Economy economy = properties.economy();
		int orbit4 = economy.dailyBonus() * economy.seasonRewardOrbit4Multiplier();
		int orbit2 = economy.dailyBonus() * economy.seasonRewardOrbit2Multiplier();
		assertThat(shards(alice)).isEqualTo(aliceShards + orbit4);
		assertThat(shards(bob)).isEqualTo(bobShards + orbit2);
		assertThat(shards(carol)).isEqualTo(carolShards);
		assertThat(seasonRewards(alice, seasonId)).isEqualTo(1);
		assertThat(fx.notifications(alice, "SEASON_REWARD")).isEqualTo(1);
		assertThat(fx.notifications(carol, "SEASON_REWARD")).isZero();

		// the season is closed once, and the next one starts when it ends
		assertThat(jdbc.queryForObject("select reset_at is not null from season where id = ?", Boolean.class,
				seasonId)).isTrue();
		Timestamp endsAt = jdbc.queryForObject("select ends_at from season where id = ?", Timestamp.class, seasonId);
		Timestamp nextEndsAt = jdbc.queryForObject("select ends_at from season where starts_at = ?", Timestamp.class,
				endsAt);
		assertThat(Duration.between(endsAt.toInstant(), nextEndsAt.toInstant()))
				.isEqualTo(Duration.ofDays(7L * properties.seasons().lengthWeeks()));

		closeEndedSeasons();
		assertThat(fx.trophies(alice)).isEqualTo(1101);
		assertThat(shards(alice)).isEqualTo(aliceShards + orbit4);
		assertThat(seasonRewards(alice, seasonId)).isEqualTo(1);
	}

	// ------------------------------------------------------------------ helpers

	/** A season of 4 weeks that ended yesterday; the start is spread out so two runs never share it. */
	private int endedSeason() {
		long offset = ThreadLocalRandom.current().nextLong(1, 86_400_000);
		return jdbc.queryForObject("insert into season (starts_at, ends_at) values (now() - interval '29 days' "
				+ "- make_interval(secs => ? / 1000.0), now() - interval '1 day') returning id", Integer.class, offset);
	}

	/** A Ranked battle played inside the season, written directly: the engine is not needed here. */
	private void rankedBattle(Player challenger, Player opponent, int seasonId) {
		jdbc.update("insert into battle (mode, status, challenger_id, opponent_id, outcome, challenger_trophies_delta, "
				+ "opponent_trophies_delta, decks_snapshot, played_at) select 'RANKED', 'PLAYED', ?, ?, 'CHALLENGER_WIN', "
				+ "0, 0, '{}'::jsonb, ends_at - interval '1 day' from season where id = ?",
				challenger.id(), opponent.id(), seasonId);
	}

	/** Runs the job's work now; the scheduled job may close the season first, with the same result. */
	private void closeEndedSeasons() {
		while (seasons.closeNextEndedSeason(clock.instant())) {
			// one season per transaction, as the job does
		}
	}

	private int result(int seasonId, Player player, String column) {
		return jdbc.queryForObject("select " + column + " from season_result where season_id = ? and player_id = ?",
				Integer.class, seasonId, player.id());
	}

	private int seasonRewards(Player player, int seasonId) {
		return jdbc.queryForObject("select count(*) from shard_transaction where player_id = ? "
				+ "and reason = 'SEASON_REWARD' and season_id = ?", Integer.class, player.id(), seasonId);
	}

	private int shards(Player player) {
		return jdbc.queryForObject("select shard_balance from player where id = ?", Integer.class, player.id());
	}
}
