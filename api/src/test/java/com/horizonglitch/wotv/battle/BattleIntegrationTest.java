package com.horizonglitch.wotv.battle;

import static com.horizonglitch.wotv.battle.BattleFixtures.body;
import static com.horizonglitch.wotv.battle.BattleFixtures.id;
import static com.horizonglitch.wotv.battle.BattleFixtures.wonByChallenger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizonglitch.wotv.battle.BattleFixtures.Cards;
import com.horizonglitch.wotv.battle.BattleFixtures.Player;
import com.jayway.jsonpath.JsonPath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Friend battles, challenges, rematches, results and replays against the real schema, with the engine playing
 * the matches. Every test creates its own cards and players.
 */
@SpringBootTest
class BattleIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private BattleExpiryJob expiryJob;

	private MockMvc mvc;
	private BattleFixtures fx;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		fx = new BattleFixtures(mvc, jdbc);
	}

	@Test
	void aFriendBattleIsPlayedAtOnceAgainstAValidDefenseDeck() throws Exception {
		Cards c = fx.newCards();
		Player alice = fx.newPlayer();
		Player bob = fx.newPlayer();
		Player carol = fx.newPlayer();
		long aliceDeck = fx.deck(alice, "Attack", c.strong());
		long bobDefense = fx.defenseDeck(bob, c.weak());
		fx.befriend(alice, bob);

		long battleId = wonByChallenger(fx.friendBattle(alice, bob, aliceDeck)
				.andExpect(jsonPath("$.mode").value("FRIEND"))
				.andExpect(jsonPath("$.challenger.username").value(alice.name()))
				.andExpect(jsonPath("$.opponent.username").value(bob.name()))
				.andExpect(jsonPath("$.challengerTrophiesDelta").doesNotExist())
				.andExpect(jsonPath("$.expiresAt").doesNotExist())
				.andExpect(jsonPath("$.playedAt").isNotEmpty())
				.andExpect(jsonPath("$.seed").doesNotExist()), 30);

		assertThat(jdbc.queryForObject("select seed is not null and opponent_deck_id = ? and challenger_deck_id = ? "
				+ "from battle where id = ?", Boolean.class, bobDefense, aliceDeck, battleId)).isTrue();
		assertThat(jdbc.queryForObject("select count(*) from battle_log where battle_id = ?", Integer.class, battleId))
				.isEqualTo(1);
		assertThat(fx.notifications(bob, "ATTACK_RECEIVED")).isEqualTo(1);
		assertThat(fx.trophies(alice)).isZero();

		fx.authed(get("/api/v1/battles/" + battleId), bob).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PLAYED"));
		fx.authed(get("/api/v1/battles/" + battleId + "/result"), bob).andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("CHALLENGER_WIN"))
				.andExpect(jsonPath("$.endReason").value("ENERGY_THRESHOLD"))
				.andExpect(jsonPath("$.winThreshold").value(30))
				.andExpect(jsonPath("$.challenger.player.username").value(alice.name()))
				.andExpect(jsonPath("$.opponent.energy").value(0))
				.andExpect(jsonPath("$.challenger.trophiesDelta").doesNotExist());

		for (String route : new String[] {"", "/result", "/replay"}) {
			fx.authed(get("/api/v1/battles/" + battleId + route), carol).andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("BATTLE_FORBIDDEN"));
		}
		fx.authed(get("/api/v1/battles/999999999"), alice).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BATTLE_NOT_FOUND"));
	}

	@Test
	void theReplayIsAValidBattleViewerDocument() throws Exception {
		Cards c = fx.newCards();
		Player alice = fx.newPlayer();
		Player bob = fx.newPlayer();
		long aliceDeck = fx.deck(alice, "Attack", c.strong());
		fx.defenseDeck(bob, c.weak());
		fx.befriend(alice, bob);
		long battleId = wonByChallenger(fx.friendBattle(alice, bob, aliceDeck), 30);

		String replay = body(fx.authed(get("/api/v1/battles/" + battleId + "/replay"), bob).andExpect(status().isOk())
				.andExpect(jsonPath("$.battleId").value((int) battleId))
				.andExpect(jsonPath("$.mode").value("FRIEND"))
				.andExpect(jsonPath("$.rules.energyWinThreshold").value(30))
				.andExpect(jsonPath("$.rules.endBonusPerCard").value(4))
				.andExpect(jsonPath("$.players[0].side").value("A"))
				.andExpect(jsonPath("$.players[0].displayName").value(alice.name()))
				.andExpect(jsonPath("$.players[1].playerId").value((int) bob.id()))
				.andExpect(jsonPath("$.events[0].type").value("CARD_DEPLOYED"))
				.andExpect(jsonPath("$.result.winnerSide").value("A"))
				.andExpect(jsonPath("$.result.endReason").value("ENERGY_THRESHOLD")));

		JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
				.getSchema(Files.readString(Path.of("..", "docs", "battle-viewer.schema.json")));
		assertThat(schema.validate(new ObjectMapper().readTree(replay))).isEmpty();
		int finalEnergy = JsonPath.read(replay, "$.result.finalEnergyBySide.A");
		assertThat(finalEnergy).isEqualTo(jdbc.queryForObject("select challenger_energy from battle where id = ?",
				Integer.class, battleId));

		// kept for 90 days: after that the replay is gone, the result stays
		jdbc.update("update battle set played_at = now() - interval '91 days' where id = ?", battleId);
		fx.authed(get("/api/v1/battles/" + battleId + "/replay"), alice).andExpect(status().isGone())
				.andExpect(jsonPath("$.code").value("REPLAY_EXPIRED"));
		expiryJob.deleteOldReplays();
		assertThat(jdbc.queryForObject("select count(*) from battle_log where battle_id = ?", Integer.class, battleId))
				.isZero();
		fx.authed(get("/api/v1/battles/" + battleId + "/result"), alice).andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("CHALLENGER_WIN"))
				.andExpect(jsonPath("$.endReason").doesNotExist());
	}

	@Test
	void refusesInvalidFriendBattles() throws Exception {
		Cards c = fx.newCards();
		Player alice = fx.newPlayer();
		Player bob = fx.newPlayer();
		Player stranger = fx.newPlayer();
		long aliceDeck = fx.deck(alice, "Attack", c.strong());
		long incomplete = fx.saveDeck(alice, "Incomplete", c.strong().subList(0, 2), 3);
		long bobDeck = fx.deck(bob, "Attack", c.weak());
		fx.befriend(bob, alice);

		fx.friendBattle(alice, stranger, aliceDeck).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_FRIENDS"));
		fx.friendBattle(alice, alice, aliceDeck).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("BATTLE_WITH_SELF"));
		fx.authed(post("/api/v1/battles/friend").contentType(MediaType.APPLICATION_JSON)
				.content("{\"opponentUsername\":\"nobody_" + bob.name() + "\",\"deckId\":" + aliceDeck + "}"), alice)
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
		fx.authed(post("/api/v1/battles/friend").contentType(MediaType.APPLICATION_JSON)
				.content("{\"opponentUsername\":\"" + bob.name() + "\"}"), alice)
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("deckId"));
		fx.friendBattle(alice, bob, incomplete).andExpect(status().is(422))
				.andExpect(jsonPath("$.code").value("DECK_INVALID"));
		fx.friendBattle(alice, bob, bobDeck).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DECK_NOT_FOUND"));
		mvc.perform(post("/api/v1/battles/friend").contentType(MediaType.APPLICATION_JSON)
				.content("{\"opponentUsername\":\"" + bob.name() + "\",\"deckId\":" + aliceDeck + "}"))
				.andExpect(status().isUnauthorized());
		assertThat(jdbc.queryForObject("select count(*) from battle where challenger_id = ?", Integer.class, alice.id()))
				.isZero();
	}

	@Test
	void withoutADefenseDeckTheBattleIsAChallengePlayedWhenAccepted() throws Exception {
		Cards c = fx.newCards();
		Player alice = fx.newPlayer();
		Player bob = fx.newPlayer();
		long aliceDeck = fx.deck(alice, "Attack", c.strong());
		long bobDeck = fx.deck(bob, "Mine", c.weak());
		fx.befriend(alice, bob);

		long battleId = id(fx.friendBattle(alice, bob, aliceDeck).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.outcome").doesNotExist())
				.andExpect(jsonPath("$.expiresAt").isNotEmpty()));
		assertThat(fx.notifications(bob, "CHALLENGE_RECEIVED")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select extract(epoch from expires_at - created_at) / 3600 from battle "
				+ "where id = ?", Double.class, battleId)).isBetween(47.9, 48.1);

		// MODE-07: one pending challenge per pair, in either direction
		fx.friendBattle(alice, bob, aliceDeck).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BATTLE_ALREADY_PENDING"));
		fx.friendBattle(bob, alice, bobDeck).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BATTLE_ALREADY_PENDING"));
		fx.authed(get("/api/v1/battles/" + battleId + "/result"), alice).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BATTLE_NOT_PLAYED"));
		fx.authed(get("/api/v1/battles/" + battleId + "/replay"), alice).andExpect(status().isConflict());
		fx.withDeck("/api/v1/battles/" + battleId + "/rematch", alice, aliceDeck).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BATTLE_NOT_PLAYED"));

		// MODE-04: the challenger's deck is the one saved when the challenge was sent
		for (long cardId : c.weak()) {
			jdbc.update("insert into collection_card (player_id, card_id, quantity) values (?, ?, 3)", alice.id(), cardId);
		}
		fx.authed(put("/api/v1/users/me/decks/" + aliceDeck).contentType(MediaType.APPLICATION_JSON)
				.content(BattleFixtures.deckJson("Attack", c.weak(), 3)), alice).andExpect(status().isOk());

		fx.withDeck("/api/v1/battles/" + battleId + "/accept", alice, aliceDeck).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("BATTLE_FORBIDDEN"));
		fx.withDeck("/api/v1/battles/" + battleId + "/accept", bob, aliceDeck).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DECK_NOT_FOUND"));
		String accepted = body(fx.withDeck("/api/v1/battles/" + battleId + "/accept", bob, bobDeck)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PLAYED"))
				.andExpect(jsonPath("$.outcome").value("CHALLENGER_WIN")));
		assertThat(JsonPath.<Integer>read(accepted, "$.challengerEnergy")).isGreaterThanOrEqualTo(30);
		assertThat(fx.notifications(alice, "CHALLENGE_ACCEPTED")).isEqualTo(1);
		assertThat(fx.notifications(bob, "ATTACK_RECEIVED")).isZero();
		assertThat(jdbc.queryForObject("select opponent_deck_id from battle where id = ?", Long.class, battleId))
				.isEqualTo(bobDeck);

		fx.withDeck("/api/v1/battles/" + battleId + "/accept", bob, bobDeck).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BATTLE_NOT_PENDING"))
				.andExpect(jsonPath("$.details.status").value("PLAYED"));
		fx.authed(get("/api/v1/battles/" + battleId + "/replay"), bob).andExpect(status().isOk())
				.andExpect(jsonPath("$.players[0].deckSnapshot.cards[0].baseAttack").value(10));
	}

	@Test
	void aChallengeCanBeRejectedCancelledOrExpire() throws Exception {
		Cards c = fx.newCards();
		Player alice = fx.newPlayer();
		Player bob = fx.newPlayer();
		long aliceDeck = fx.deck(alice, "Attack", c.strong());
		long bobDeck = fx.deck(bob, "Mine", c.weak());
		fx.befriend(alice, bob);

		long rejected = id(fx.friendBattle(alice, bob, aliceDeck).andExpect(status().isCreated()));
		fx.authed(post("/api/v1/battles/" + rejected + "/cancel"), bob).andExpect(status().isForbidden());
		fx.authed(post("/api/v1/battles/" + rejected + "/reject"), alice).andExpect(status().isForbidden());
		fx.authed(post("/api/v1/battles/" + rejected + "/reject"), bob).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"));
		assertThat(fx.notifications(alice, "CHALLENGE_REJECTED")).isEqualTo(1);

		long cancelled = id(fx.friendBattle(alice, bob, aliceDeck).andExpect(status().isCreated()));
		fx.authed(post("/api/v1/battles/" + cancelled + "/cancel"), alice).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		assertThat(fx.notifications(bob, "CHALLENGE_CANCELLED")).isEqualTo(1);
		fx.withDeck("/api/v1/battles/" + cancelled + "/accept", bob, bobDeck).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BATTLE_NOT_PENDING"))
				.andExpect(jsonPath("$.details.status").value("CANCELLED"));

		// MODE-06: expired when someone acts on it past the deadline, even before the job runs
		long expired = id(fx.friendBattle(alice, bob, aliceDeck).andExpect(status().isCreated()));
		jdbc.update("update battle set expires_at = now() - interval '1 minute' where id = ?", expired);
		fx.withDeck("/api/v1/battles/" + expired + "/accept", bob, bobDeck).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BATTLE_EXPIRED"));
		fx.authed(get("/api/v1/battles/" + expired), alice).andExpect(jsonPath("$.status").value("EXPIRED"));
		assertThat(fx.notifications(alice, "CHALLENGE_EXPIRED")).isEqualTo(1);
		assertThat(fx.notifications(bob, "CHALLENGE_EXPIRED")).isEqualTo(1);

		// ... or by the job; an expired challenge frees the pair
		long expiredByJob = id(fx.friendBattle(alice, bob, aliceDeck).andExpect(status().isCreated()));
		jdbc.update("update battle set expires_at = now() - interval '1 minute' where id = ?", expiredByJob);
		expiryJob.expireDueChallenges();
		fx.authed(get("/api/v1/battles/" + expiredByJob), bob).andExpect(jsonPath("$.status").value("EXPIRED"));
		fx.friendBattle(bob, alice, bobDeck).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"));

		fx.authed(get("/api/v1/users/me/battles").param("status", "PENDING"), alice).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(1))
				.andExpect(jsonPath("$.items[0].challenger.username").value(bob.name()));
		fx.authed(get("/api/v1/users/me/battles"), alice).andExpect(jsonPath("$.totalItems").value(5));
	}

	@Test
	void eitherSummonerCanAskForARematchOfAFriendBattle() throws Exception {
		Cards c = fx.newCards();
		Player alice = fx.newPlayer();
		Player bob = fx.newPlayer();
		Player carol = fx.newPlayer();
		long aliceDeck = fx.deck(alice, "Attack", c.strong());
		long bobDefense = fx.defenseDeck(bob, c.weak());
		long carolDeck = fx.deck(carol, "Mine", c.weak());
		fx.befriend(alice, bob);
		long original = wonByChallenger(fx.friendBattle(alice, bob, aliceDeck), 30);

		fx.withDeck("/api/v1/battles/" + original + "/rematch", carol, carolDeck).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("BATTLE_FORBIDDEN"));

		// asked by the loser: alice has no defense deck, so the rematch is a challenge (MODE-23)
		long rematch = id(fx.withDeck("/api/v1/battles/" + original + "/rematch", bob, bobDefense)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.mode").value("FRIEND"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.rematchOfId").value((int) original))
				.andExpect(jsonPath("$.challenger.username").value(bob.name()))
				.andExpect(jsonPath("$.opponent.username").value(alice.name())));
		fx.withDeck("/api/v1/battles/" + rematch + "/accept", alice, aliceDeck).andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("OPPONENT_WIN"));

		// asked by the winner, any number of times: played at once against bob's defense deck
		wonByChallenger(fx.withDeck("/api/v1/battles/" + original + "/rematch", alice, aliceDeck)
				.andExpect(jsonPath("$.rematchOfId").value((int) original)), 30);
		wonByChallenger(fx.withDeck("/api/v1/battles/" + rematch + "/rematch", alice, aliceDeck), 30);

		fx.authed(get("/api/v1/users/me/battles").param("result", "WIN"), alice)
				.andExpect(jsonPath("$.totalItems").value(4));
		fx.authed(get("/api/v1/users/me/battles").param("result", "LOSS"), alice)
				.andExpect(jsonPath("$.totalItems").value(0));
		fx.authed(get("/api/v1/users/me/battles").param("result", "LOSS").param("mode", "FRIEND"), bob)
				.andExpect(jsonPath("$.totalItems").value(4));
		fx.authed(get("/api/v1/users/me/battles").param("mode", "RANKED"), bob)
				.andExpect(jsonPath("$.totalItems").value(0));
		fx.authed(get("/api/v1/users/me/battles").param("result", "BEST"), bob).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("RESULT_INVALID"));
		fx.authed(get("/api/v1/users/me/battles").param("mode", "SOLO"), bob).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void deletingTheAccountCancelsItsPendingChallenges() throws Exception {
		Cards c = fx.newCards();
		Player alice = fx.newPlayer();
		Player bob = fx.newPlayer();
		long aliceDeck = fx.deck(alice, "Attack", c.strong());
		fx.befriend(alice, bob);
		long battleId = id(fx.friendBattle(alice, bob, aliceDeck).andExpect(status().isCreated()));

		fx.authed(delete("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"" + BattleFixtures.PASSWORD + "\"}"), alice).andExpect(status().isNoContent());

		fx.authed(get("/api/v1/battles/" + battleId), bob).andExpect(jsonPath("$.status").value("CANCELLED"));
		assertThat(fx.notifications(bob, "CHALLENGE_CANCELLED")).isEqualTo(1);
	}
}
