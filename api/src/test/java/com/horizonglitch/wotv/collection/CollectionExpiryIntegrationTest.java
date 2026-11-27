package com.horizonglitch.wotv.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.horizonglitch.wotv.battle.BattleFixtures;
import com.horizonglitch.wotv.battle.BattleFixtures.Cards;
import com.horizonglitch.wotv.battle.BattleFixtures.Player;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class CollectionExpiryIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private CollectionExpiryJob expiryJob;

	private MockMvc mvc;
	private BattleFixtures fixtures;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		fixtures = new BattleFixtures(mvc, jdbc);
	}

	@Test
	void scheduledExpiryArchivesWandererInvalidatesDeckAndNotifiesOnce() throws Exception {
		Player player = fixtures.newPlayer();
		Cards cards = fixtures.newCards();
		long wandererId = createWanderer();

		List<Long> deckCardIds = new ArrayList<>(cards.strong());
		List<Integer> quantities = new ArrayList<>(List.of(3, 3, 3, 3, 3, 3, 3, 3, 3, 2));
		for (int i = 0; i < deckCardIds.size(); i++) {
			jdbc.update("insert into collection_card (player_id, card_id, quantity) values (?, ?, ?)",
					player.id(), deckCardIds.get(i), quantities.get(i));
		}
		jdbc.update("""
				insert into collection_card (player_id, card_id, quantity, acquired_at, expires_at)
				values (?, ?, 1, now(), now() + interval '7 days')
				""", player.id(), wandererId);

		String deckRequest = deckRequest(deckCardIds, quantities, wandererId);
		String deckResponse = authed(post("/api/v1/users/me/decks")
				.contentType(MediaType.APPLICATION_JSON)
				.content(deckRequest), player)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("VALID"))
				.andReturn().getResponse().getContentAsString();
		long deckId = ((Number) JsonPath.read(deckResponse, "$.id")).longValue();

		expiryJob.departDueWanderers();
		assertThat(collectionCount(player.id(), wandererId)).isEqualTo(1);
		assertThat(archiveCount(player.id(), wandererId)).isZero();
		assertThat(notificationCount(player.id(), "WANDERER_DEPARTED")).isZero();

		jdbc.update("""
				update collection_card
				set acquired_at = now() - interval '7 days' - interval '1 second',
				    expires_at = now() - interval '1 second'
				where player_id = ? and card_id = ?
				""", player.id(), wandererId);

		expiryJob.departDueWanderers();
		assertThat(collectionCount(player.id(), wandererId)).isZero();
		assertThat(archiveCount(player.id(), wandererId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select status from deck where id = ?", String.class, deckId))
				.isEqualTo("INVALID");
		assertThat(notificationCount(player.id(), "WANDERER_DEPARTED")).isEqualTo(1);
		assertThat(notificationCount(player.id(), "DECK_INVALID")).isEqualTo(1);

		expiryJob.departDueWanderers();
		assertThat(archiveCount(player.id(), wandererId)).isEqualTo(1);
		assertThat(notificationCount(player.id(), "WANDERER_DEPARTED")).isEqualTo(1);
		assertThat(notificationCount(player.id(), "DECK_INVALID")).isEqualTo(1);
		authed(get("/api/v1/users/me/decks/" + deckId), player)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("INVALID"));
	}

	private long createWanderer() {
		int factionId = jdbc.queryForObject("insert into faction (name, is_inert) values (?, true) returning id",
				Integer.class, "expiry_" + UUID.randomUUID());
		return jdbc.queryForObject("""
				insert into card (name, faction_id, rarity_id, attack, defense)
				select ?, ?, id, 1, 31 from rarity where name = 'Wanderer'
				returning id
				""", Long.class, "expiry_" + UUID.randomUUID(), factionId);
	}

	private String deckRequest(List<Long> cardIds, List<Integer> quantities, long wandererId) {
		String cardsJson = java.util.stream.IntStream.range(0, cardIds.size())
				.mapToObj(i -> "{\"cardId\":" + cardIds.get(i) + ",\"quantity\":" + quantities.get(i) + "}")
				.collect(java.util.stream.Collectors.joining(","));
		return "{\"name\":\"Wanderer expiry\", \"cards\":[" + cardsJson
				+ ",{\"cardId\":" + wandererId + ",\"quantity\":1}]}";
	}

	private int collectionCount(long playerId, long cardId) {
		return jdbc.queryForObject("select count(*) from collection_card where player_id = ? and card_id = ?",
				Integer.class, playerId, cardId);
	}

	private int archiveCount(long playerId, long cardId) {
		return jdbc.queryForObject("select count(*) from memory_archive where player_id = ? and card_id = ?",
				Integer.class, playerId, cardId);
	}

	private int notificationCount(long playerId, String type) {
		return jdbc.queryForObject("select count(*) from notification where player_id = ? and type = ?",
				Integer.class, playerId, type);
	}

	private ResultActions authed(MockHttpServletRequestBuilder request, Player player) throws Exception {
		return mvc.perform(request.header("Authorization", "Bearer " + player.token()));
	}
}
