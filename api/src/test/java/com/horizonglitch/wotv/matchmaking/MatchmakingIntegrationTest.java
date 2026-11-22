package com.horizonglitch.wotv.matchmaking;

import static com.horizonglitch.wotv.battle.BattleFixtures.body;
import static com.horizonglitch.wotv.battle.BattleFixtures.id;
import static com.horizonglitch.wotv.battle.BattleFixtures.wonByChallenger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.horizonglitch.wotv.battle.BattleFixtures;
import com.horizonglitch.wotv.battle.BattleFixtures.Cards;
import com.horizonglitch.wotv.battle.BattleFixtures.Player;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Opponent lists, Quick and Ranked attacks, trophies, attack limits and shield against the real schema. The
 * database is shared with the other tests, so attacks use a list written by the test, and the generation of a
 * Ranked list is checked on players placed alone at a very high number of trophies.
 */
@SpringBootTest
class MatchmakingIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mvc;
	private BattleFixtures fx;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		fx = new BattleFixtures(mvc, jdbc);
	}

	@Test
	void aRankedListHoldsFiveOpponentsInRangeAndIsSaved() throws Exception {
		int trophies = ThreadLocalRandom.current().nextInt(10_000_000, 2_000_000_000);
		Player me = fx.newPlayer();
		fx.setTrophies(me, trophies);
		List<Integer> expected = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			Player opponent = fx.newPlayer();
			fx.setTrophies(opponent, trophies + 10 * i - 20);
			storedDefenseDeck(opponent, "VALID");
			expected.add((int) opponent.id());
		}
		// MODE-15: never a Summoner with a shield, without a defense deck or with an invalid one
		Player shielded = fx.newPlayer();
		fx.setTrophies(shielded, trophies);
		storedDefenseDeck(shielded, "VALID");
		jdbc.update("update player set protected_until = now() + interval '1 hour' where id = ?", shielded.id());
		Player noDefense = fx.newPlayer();
		fx.setTrophies(noDefense, trophies);
		Player invalidDefense = fx.newPlayer();
		fx.setTrophies(invalidDefense, trophies);
		storedDefenseDeck(invalidDefense, "INVALID");

		String first = body(fx.authed(get("/api/v1/matchmaking/opponents").param("mode", "RANKED"), me)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("RANKED"))
				.andExpect(jsonPath("$.opponents.length()").value(5))
				.andExpect(jsonPath("$.opponents[0].position").value(1))
				.andExpect(jsonPath("$.opponents[0].attacked").value(false))
				.andExpect(jsonPath("$.opponents[0].orbit").value("ORBIT_4")));
		List<Integer> ids = JsonPath.read(first, "$.opponents[*].opponent.id");
		assertThat(ids).containsExactlyInAnyOrderElementsOf(expected);

		// MODE-09: the list is saved, reloading does not change it
		String again = body(fx.authed(get("/api/v1/matchmaking/opponents").param("mode", "RANKED"), me));
		assertThat(JsonPath.<List<Integer>>read(again, "$.opponents[*].opponent.id")).containsExactlyElementsOf(ids);
		// same instant: the first answer has the precision of the clock, the second the one of the database
		assertThat(Instant.parse(JsonPath.<String>read(again, "$.generatedAt")))
				.isCloseTo(Instant.parse(JsonPath.<String>read(first, "$.generatedAt")), within(1, ChronoUnit.MILLIS));

		fx.authed(get("/api/v1/matchmaking/opponents").param("mode", "FRIEND"), me).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("MODE_INVALID"));
		fx.authed(get("/api/v1/matchmaking/opponents"), me).andExpect(status().isBadRequest());
	}

	@Test
	void aRankedListWidensTheRangeUntilItIsFull() throws Exception {
		// MODE-13: alone at the top, the range is doubled until it reaches the other Summoners
		int trophies = ThreadLocalRandom.current().nextInt(10_000_000, 2_000_000_000);
		Player me = fx.newPlayer();
		fx.setTrophies(me, trophies);
		for (int i = 0; i < 5; i++) {
			Player opponent = fx.newPlayer();
			fx.setTrophies(opponent, trophies - 3_000 - i);
			storedDefenseDeck(opponent, "VALID");
		}

		fx.authed(get("/api/v1/matchmaking/opponents").param("mode", "RANKED"), me).andExpect(status().isOk())
				.andExpect(jsonPath("$.opponents.length()").value(5));
	}

	@Test
	void aQuickListHoldsActiveSummonersAndRefreshHasACooldown() throws Exception {
		Player me = fx.newPlayer();
		List<Player> others = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			others.add(fx.newPlayer());
		}
		Player inactive = others.get(0);
		Player activeWithoutRecentLogin = others.get(1);
		jdbc.update("update player set last_active_at = now() - interval '8 days'");
		for (Player candidate : others.subList(1, others.size())) {
			jdbc.update("update player set last_active_at = now() where id = ?", candidate.id());
		}
		jdbc.update("update player set last_active_at = now() - interval '8 days' where id = ?", inactive.id());
		jdbc.update("update player set last_daily_bonus_at = now() - interval '8 days' where id = ?",
				activeWithoutRecentLogin.id());

		String list = body(fx.authed(get("/api/v1/matchmaking/opponents").param("mode", "QUICK"), me)
				.andExpect(status().isOk()).andExpect(jsonPath("$.opponents.length()").value(5)));
		List<Integer> ids = JsonPath.read(list, "$.opponents[*].opponent.id");
		assertThat(ids).doesNotHaveDuplicates().doesNotContain((int) me.id(), (int) inactive.id());
		assertThat(ids).contains((int) activeWithoutRecentLogin.id());
		for (int opponentId : ids) {
			// MODE-14: active in the last 7 days
			assertThat(jdbc.queryForObject("select deleted_at is null and last_active_at > now() - interval "
					+ "'7 days' from player where id = ?", Boolean.class, opponentId)).isTrue();
		}

		// MODE-11: the first refresh is free, the next one waits 5 seconds
		refresh(me, "QUICK").andExpect(status().isOk()).andExpect(jsonPath("$.opponents.length()").value(5));
		refresh(me, "QUICK").andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("MATCHMAKING_COOLDOWN"))
				.andExpect(jsonPath("$.details.refreshAvailableAt").isNotEmpty());
		jdbc.update("update opponent_list set last_refresh_at = now() - interval '6 seconds' where player_id = ?",
				me.id());
		refresh(me, "QUICK").andExpect(status().isOk());
		refresh(me, "FRIEND").andExpect(status().isBadRequest());
	}

	@Test
	void aRankedAttackIsPlayedAtOnceAndMovesTheTrophies() throws Exception {
		Cards c = fx.newCards();
		Player alice = fx.newPlayer();
		Player bob = fx.newPlayer();
		Player carol = fx.newPlayer();
		long aliceDeck = fx.defenseDeck(alice, c.strong());
		fx.defenseDeck(bob, c.weak());
		fx.setTrophies(alice, 1000);
		fx.setTrophies(bob, 1000);
		fx.giveList(alice, "RANKED", bob);

		fx.attack(alice, carol, "RANKED", aliceDeck).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OPPONENT_NOT_IN_LIST"));
		fx.attack(alice, bob, "QUICK", aliceDeck).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OPPONENT_NOT_IN_LIST"));
		fx.attack(alice, bob, "FRIEND", aliceDeck).andExpect(status().isBadRequest());

		long battleId = wonByChallenger(fx.attack(alice, bob, "RANKED", aliceDeck)
				.andExpect(jsonPath("$.mode").value("RANKED"))
				.andExpect(jsonPath("$.challengerTrophiesDelta").value(16))
				.andExpect(jsonPath("$.opponentTrophiesDelta").value(-16)), 30);
		assertThat(fx.trophies(alice)).isEqualTo(1016);
		assertThat(fx.trophies(bob)).isEqualTo(984);
		assertThat(fx.notifications(bob, "ATTACK_RECEIVED")).isEqualTo(1);
		fx.authed(get("/api/v1/battles/" + battleId + "/result"), bob)
				.andExpect(jsonPath("$.opponent.trophiesDelta").value(-16));

		// MODE-10: each opponent only once per list
		fx.authed(get("/api/v1/matchmaking/opponents").param("mode", "RANKED"), alice)
				.andExpect(jsonPath("$.opponents[0].attacked").value(true))
				.andExpect(jsonPath("$.opponents[0].battleId").value((int) battleId));
		fx.attack(alice, bob, "RANKED", aliceDeck).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OPPONENT_ALREADY_ATTACKED"));

		// MODE-16: a new list does not allow a second attack within 24 hours
		fx.giveList(alice, "RANKED", bob);
		fx.attack(alice, bob, "RANKED", aliceDeck).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ATTACK_LIMIT_REACHED"));
		jdbc.update("update battle set played_at = now() - interval '25 hours' where id = ?", battleId);
		wonByChallenger(fx.attack(alice, bob, "RANKED", aliceDeck), 30);

		// MODE-24: no rematch in Ranked
		fx.withDeck("/api/v1/battles/" + battleId + "/rematch", bob, aliceDeck).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("REMATCH_NOT_ALLOWED"));
	}

	@Test
	void trophiesNeverGoBelowZero() throws Exception {
		Cards c = fx.newCards();
		Player alice = fx.newPlayer();
		Player bob = fx.newPlayer();
		long aliceDeck = fx.defenseDeck(alice, c.strong());
		fx.defenseDeck(bob, c.weak());
		fx.giveList(alice, "RANKED", bob);

		// RANK-05: the loser at 0 stays at 0, the winner gains the full amount
		wonByChallenger(fx.attack(alice, bob, "RANKED", aliceDeck)
				.andExpect(jsonPath("$.challengerTrophiesDelta").value(16))
				.andExpect(jsonPath("$.opponentTrophiesDelta").value(0)), 30);
		assertThat(fx.trophies(alice)).isEqualTo(16);
		assertThat(fx.trophies(bob)).isZero();
	}

	@Test
	void rankedPromotionGrantsOrbitRewardOnceForTheCurrentSeason() throws Exception {
		Cards c = fx.newCards();
		Player alice = fx.newPlayer();
		Player bob = fx.newPlayer();
		long aliceDeck = fx.defenseDeck(alice, c.strong());
		fx.defenseDeck(bob, c.weak());
		fx.setTrophies(alice, 190);
		fx.setTrophies(bob, 190);
		fx.giveList(alice, "RANKED", bob);
		int balanceBefore = jdbc.queryForObject("select shard_balance from player where id = ?", Integer.class, alice.id());
		Integer seasonId = jdbc.queryForObject("select id from season where reset_at is null and starts_at <= now() "
				+ "and ends_at > now() order by starts_at desc limit 1", Integer.class);

		wonByChallenger(fx.attack(alice, bob, "RANKED", aliceDeck)
				.andExpect(jsonPath("$.challengerTrophiesDelta").value(16)), 30);

		assertThat(jdbc.queryForObject("select shard_balance from player where id = ?", Integer.class, alice.id()))
				.isEqualTo(balanceBefore + 200);
		assertThat(jdbc.queryForObject("select count(*) from shard_transaction where player_id = ? "
				+ "and reason = 'ORBIT_REWARD' and season_id = ? and orbit = 2 and amount = 200",
				Integer.class, alice.id(), seasonId)).isEqualTo(1);
		assertThat(fx.notifications(alice, "ORBIT_REWARD")).isEqualTo(1);
	}

	@Test
	void rankedNeedsAValidDefenseDeckFromBothSummoners() throws Exception {
		Cards c = fx.newCards();
		Player alice = fx.newPlayer();
		Player bob = fx.newPlayer();
		long aliceDeck = fx.deck(alice, "Attack", c.strong());
		fx.defenseDeck(bob, c.weak());
		fx.giveList(alice, "RANKED", bob);

		// MODE-08
		fx.attack(alice, bob, "RANKED", aliceDeck).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DEFENSE_DECK_REQUIRED"));
		fx.authed(put("/api/v1/users/me/decks/" + aliceDeck + "/defense"), alice).andExpect(status().isOk());

		// bob's defense deck stops being valid: he lost a card it needs
		jdbc.update("delete from collection_card where player_id = ? and card_id = ?", bob.id(), c.weak().get(0));
		fx.attack(alice, bob, "RANKED", aliceDeck).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OPPONENT_NOT_AVAILABLE"));
		jdbc.update("insert into collection_card (player_id, card_id, quantity) values (?, ?, 3)", bob.id(),
				c.weak().get(0));

		long incomplete = fx.saveDeck(alice, "Incomplete", c.strong().subList(0, 2), 3);
		fx.attack(alice, bob, "RANKED", incomplete).andExpect(status().is(422))
				.andExpect(jsonPath("$.code").value("DECK_INVALID"));
		wonByChallenger(fx.attack(alice, bob, "RANKED", aliceDeck), 30);
	}

	@Test
	void fiveRankedAttacksInADayGiveAShieldThatEndsWhenTheSummonerAttacks() throws Exception {
		Cards c = fx.newCards();
		Player victim = fx.newPlayer();
		long victimDeck = fx.defenseDeck(victim, c.weak());
		List<Player> attackers = new ArrayList<>();
		List<Long> decks = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			Player attacker = fx.newPlayer();
			attackers.add(attacker);
			decks.add(fx.defenseDeck(attacker, c.strong()));
			fx.giveList(attacker, "RANKED", victim);
		}

		// MODE-19
		for (int i = 0; i < 4; i++) {
			wonByChallenger(fx.attack(attackers.get(i), victim, "RANKED", decks.get(i)), 30);
		}
		assertThat(shieldHours(victim)).isNull();
		wonByChallenger(fx.attack(attackers.get(4), victim, "RANKED", decks.get(4)), 30);
		assertThat(shieldHours(victim)).isBetween(11.9, 12.1);

		// MODE-20
		fx.attack(attackers.get(5), victim, "RANKED", decks.get(5)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OPPONENT_SHIELDED"));

		// MODE-21: the shield ends at once when its owner attacks in Ranked
		fx.giveList(victim, "RANKED", attackers.get(0));
		fx.attack(victim, attackers.get(0), "RANKED", victimDeck).andExpect(status().isCreated())
				.andExpect(jsonPath("$.outcome").value("OPPONENT_WIN"));
		assertThat(shieldHours(victim)).isLessThanOrEqualTo(0.0);

		// the attacks counted for that shield do not count for the next one
		wonByChallenger(fx.attack(attackers.get(5), victim, "RANKED", decks.get(5)), 30);
		assertThat(shieldHours(victim)).isLessThanOrEqualTo(0.0);
	}

	@Test
	void aQuickAttackWithoutDefenseDeckIsAChallengeAndLeavesTheOpponentAvailable() throws Exception {
		Cards c = fx.newCards();
		Player alice = fx.newPlayer();
		Player bob = fx.newPlayer();
		long aliceDeck = fx.deck(alice, "Attack", c.strong());
		long bobDeck = fx.deck(bob, "Mine", c.weak());
		fx.setTrophies(alice, 500);
		fx.setTrophies(bob, 500);
		fx.giveList(alice, "QUICK", bob);

		// MODE-14: no friendship and no defense deck needed in Quick
		long battleId = id(fx.attack(alice, bob, "QUICK", aliceDeck).andExpect(status().isCreated())
				.andExpect(jsonPath("$.mode").value("QUICK"))
				.andExpect(jsonPath("$.status").value("PENDING")));
		assertThat(fx.notifications(bob, "CHALLENGE_RECEIVED")).isEqualTo(1);
		fx.authed(get("/api/v1/matchmaking/opponents").param("mode", "QUICK"), alice)
				.andExpect(jsonPath("$.opponents[0].attacked").value(false));
		fx.attack(alice, bob, "QUICK", aliceDeck).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BATTLE_ALREADY_PENDING"));

		// the entry is linked when the challenge is accepted and played; Quick wins at 20 Energy, no trophies
		String played = body(fx.withDeck("/api/v1/battles/" + battleId + "/accept", bob, bobDeck)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PLAYED"))
				.andExpect(jsonPath("$.outcome").value("CHALLENGER_WIN"))
				.andExpect(jsonPath("$.challengerTrophiesDelta").doesNotExist()));
		assertThat(JsonPath.<Integer>read(played, "$.challengerEnergy")).isBetween(20, 29 + 12);
		fx.authed(get("/api/v1/battles/" + battleId + "/result"), alice)
				.andExpect(jsonPath("$.winThreshold").value(20));
		fx.authed(get("/api/v1/matchmaking/opponents").param("mode", "QUICK"), alice)
				.andExpect(jsonPath("$.opponents[0].attacked").value(true))
				.andExpect(jsonPath("$.opponents[0].battleId").value((int) battleId));
		assertThat(fx.trophies(alice)).isEqualTo(500);
		assertThat(fx.trophies(bob)).isEqualTo(500);
		fx.attack(alice, bob, "QUICK", aliceDeck).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OPPONENT_ALREADY_ATTACKED"));
	}

	@Test
	void quickAllowsThreeAttacksADayOnTheSameOpponentAndRematchesAreFree() throws Exception {
		Cards c = fx.newCards();
		Player alice = fx.newPlayer();
		Player bob = fx.newPlayer();
		long aliceDeck = fx.deck(alice, "Attack", c.strong());
		fx.defenseDeck(bob, c.weak());

		// MODE-17
		long last = 0;
		for (int i = 0; i < 3; i++) {
			fx.giveList(alice, "QUICK", bob);
			last = wonByChallenger(fx.attack(alice, bob, "QUICK", aliceDeck), 20);
		}
		fx.giveList(alice, "QUICK", bob);
		fx.attack(alice, bob, "QUICK", aliceDeck).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ATTACK_LIMIT_REACHED"))
				.andExpect(jsonPath("$.details.limit").value(3));
		assertThat(fx.notifications(bob, "ATTACK_RECEIVED")).isEqualTo(3);

		// MODE-23: a rematch is not an attack from the list
		wonByChallenger(fx.withDeck("/api/v1/battles/" + last + "/rematch", alice, aliceDeck)
				.andExpect(jsonPath("$.mode").value("QUICK"))
				.andExpect(jsonPath("$.rematchOfId").value((int) last)), 20);

		// MODE-22: Quick battles never start a shield
		assertThat(shieldHours(bob)).isNull();
	}

	// ------------------------------------------------------------------ helpers

	private ResultActions refresh(Player player, String mode) throws Exception {
		return fx.authed(post("/api/v1/matchmaking/opponents/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"mode\":\"" + mode + "\"}"), player);
	}

	/** A defense deck with only its stored status: enough for the generation of the Ranked list. */
	private void storedDefenseDeck(Player player, String status) {
		long deckId = jdbc.queryForObject("insert into deck (player_id, name, status) values (?, 'Defense', ?) "
				+ "returning id", Long.class, player.id(), status);
		jdbc.update("update player set defense_deck_id = ? where id = ?", deckId, player.id());
	}

	/** Hours left of the shield: null without one, zero or less when it has ended. */
	private Double shieldHours(Player player) {
		return jdbc.queryForObject("select extract(epoch from protected_until - now()) / 3600 from player "
				+ "where id = ?", Double.class, player.id());
	}
}
