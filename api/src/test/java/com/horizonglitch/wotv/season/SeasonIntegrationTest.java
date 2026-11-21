package com.horizonglitch.wotv.season;

import static org.assertj.core.api.Assertions.assertThat;
import static com.horizonglitch.wotv.battle.BattleFixtures.Player;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.horizonglitch.wotv.battle.BattleFixtures;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class SeasonIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mvc;
	private BattleFixtures fixtures;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		fixtures = new BattleFixtures(mvc, jdbc);
	}

	@Test
	void currentSeasonAndRankedLeaderboardUseTheActiveSeasonAndOrbitFilter() throws Exception {
		Player alice = fixtures.newPlayer();
		Player bob = fixtures.newPlayer();
		jdbc.update("update player set trophies = 750 where id in (?, ?)", alice.id(), bob.id());
		jdbc.update("""
				insert into battle (mode, status, challenger_id, opponent_id, outcome, challenger_energy,
				                    opponent_energy, challenger_trophies_delta, opponent_trophies_delta,
				                    decks_snapshot, played_at)
				values ('RANKED', 'PLAYED', ?, ?, 'CHALLENGER_WIN', 30, 0, 16, -16, '{}'::jsonb, now())
				""", alice.id(), bob.id());

		fixtures.authed(get("/api/v1/seasons/current"), alice)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.startsAt").isNotEmpty())
				.andExpect(jsonPath("$.endsAt").isNotEmpty());

		String leaderboard = fixtures.authed(get("/api/v1/leaderboard?orbit=ORBIT_3&size=100"), alice)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.username == '%s')]".formatted(alice.name())).isNotEmpty())
				.andExpect(jsonPath("$.items[?(@.username == '%s')]".formatted(bob.name())).isNotEmpty())
				.andExpect(jsonPath("$.items[?(@.username == '%s')].orbit".formatted(alice.name())).value("ORBIT_3"))
				.andReturn().getResponse().getContentAsString();
		List<Integer> alicePositions = JsonPath.read(leaderboard,
				"$.items[?(@.username == '%s')].position".formatted(alice.name()));
		List<Integer> bobPositions = JsonPath.read(leaderboard,
				"$.items[?(@.username == '%s')].position".formatted(bob.name()));
		assertThat(alicePositions).containsExactlyElementsOf(bobPositions);

		fixtures.authed(get("/api/v1/leaderboard?orbit=ORBIT_1"), alice)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.username == '%s')]".formatted(alice.name())).isEmpty());
	}
}
