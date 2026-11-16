package com.horizonglitch.wotv.deck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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

/**
 * Catalog, collection and deck routes against the real schema. Every test creates its own factions, rarities,
 * cards and player, with unique names, so tests do not interfere.
 */
@SpringBootTest
class DeckIntegrationTest {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void catalogListsActiveCardsWithFiltersAndGameData() throws Exception {
		Catalog c = newCatalog();
		jdbc.update("update card set is_active = false where id = ?", c.lava.get(4));

		mvc.perform(get("/api/v1/cards").param("factionId", String.valueOf(c.lavaId)).param("sort", "name"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(9))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.items[0].faction.name").value(c.prefix + "Lava"))
				.andExpect(jsonPath("$.items[0].rarity.maxCopiesPerDeck").value(3))
				.andExpect(jsonPath("$.items[0].rarity.totalPoints").doesNotExist());
		mvc.perform(get("/api/v1/cards").param("search", c.prefix + "lava_card_1"))
				.andExpect(jsonPath("$.totalItems").value(1));
		mvc.perform(get("/api/v1/cards").param("size", "101")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("SIZE_INVALID"));
		mvc.perform(get("/api/v1/cards").param("sort", "power")).andExpect(status().isBadRequest());

		mvc.perform(get("/api/v1/cards/" + c.lava.get(0))).andExpect(status().isOk())
				.andExpect(jsonPath("$.attack").value(5));
		mvc.perform(get("/api/v1/cards/" + c.lava.get(4))).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));

		mvc.perform(get("/api/v1/game-data")).andExpect(status().isOk())
				.andExpect(jsonPath("$.deckRules.deckSize").value(30))
				.andExpect(jsonPath("$.deckRules.maxPerElementalFaction").value(15))
				.andExpect(jsonPath("$.deckRules.maxSavedDecks").value(20))
				.andExpect(jsonPath("$.advantages[?(@.dominantFactionId == " + c.lavaId + ")].value").value(3));
	}

	@Test
	void showsTheCollectionAndTheArchive() throws Exception {
		Catalog c = newCatalog();
		String token = newPlayer(c);
		jdbc.update("update collection_card set acquired_at = now() - interval '8 days', "
				+ "expires_at = now() - interval '1 day' where card_id = ?", c.wanderer);
		jdbc.update("insert into memory_archive (player_id, card_id, arrived_at, departed_at) "
				+ "select player_id, card_id, now() - interval '20 days', now() - interval '13 days' "
				+ "from collection_card where card_id = ?", c.wanderer);

		authed(get("/api/v1/users/me/collection"), token).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(12));
		authed(get("/api/v1/users/me/collection").param("availableInDeck", "true"), token)
				.andExpect(jsonPath("$.totalItems").value(11));
		authed(get("/api/v1/users/me/collection").param("factionId", String.valueOf(c.iceId)), token)
				.andExpect(jsonPath("$.totalItems").value(5));
		authed(get("/api/v1/users/me/collection/" + c.lava.get(0)), token).andExpect(status().isOk())
				.andExpect(jsonPath("$.quantity").value(3))
				.andExpect(jsonPath("$.expiresAt").doesNotExist());
		authed(get("/api/v1/users/me/archive"), token).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].card.id").value(c.wanderer));
		authed(get("/api/v1/users/me/collection/999999999"), token).andExpect(status().isNotFound());
	}

	@Test
	void savesADeckAndComputesItsStatus() throws Exception {
		Catalog c = newCatalog();
		String token = newPlayer(c);

		String created = createDeck(token, "Full deck", deckJson(c, 3, 3)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("VALID"))
				.andExpect(jsonPath("$.totalCards").value(30))
				.andExpect(jsonPath("$.isDefenseDeck").value(false))
				.andExpect(jsonPath("$.validationErrors").isEmpty())
				.andReturn().getResponse().getContentAsString();
		long deckId = ((Number) JsonPath.read(created, "$.id")).longValue();

		createDeck(token, "Half deck", deckJson(c, 2, 1)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("INCOMPLETE"))
				.andExpect(jsonPath("$.validationErrors[0].code").value("DECK_SIZE"));
		createDeck(token, "Full deck", deckJson(c, 1, 1)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DECK_NAME_ALREADY_USED"));

		authed(put("/api/v1/users/me/decks/" + deckId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Renamed\",\"cards\":" + deckJson(c, 3, 2) + "}"), token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Renamed"))
				.andExpect(jsonPath("$.status").value("INCOMPLETE"))
				.andExpect(jsonPath("$.totalCards").value(25));
		authed(get("/api/v1/users/me/decks"), token).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void refusesDuplicatesUnknownAndNotOwnedCards() throws Exception {
		Catalog c = newCatalog();
		String token = newPlayer(c);
		long lava = c.lava.get(0);

		createDeck(token, "Dup", "[{\"cardId\":" + lava + ",\"quantity\":1},{\"cardId\":" + lava + ",\"quantity\":1}]")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("CARD_DUPLICATED"));
		createDeck(token, "Unknown", "[{\"cardId\":999999999,\"quantity\":1}]")
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));
		createDeck(token, "Too many", "[{\"cardId\":" + lava + ",\"quantity\":4}]")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CARD_NOT_OWNED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("cards[0]"));
		createDeck(token, "", "[]").andExpect(status().isBadRequest());
	}

	@Test
	void reportsBrokenRulesAndSetsOnlyAValidDefenseDeck() throws Exception {
		Catalog c = newCatalog();
		String token = newPlayer(c);

		authed(post("/api/v1/users/me/decks/validate").contentType(MediaType.APPLICATION_JSON)
				.content("{\"cards\":" + lavaHeavyJson(c) + "}"), token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("INVALID"))
				.andExpect(jsonPath("$.validationErrors[?(@.code=='FACTION_LIMIT')].factionId").value(c.lavaId));

		String invalid = createDeck(token, "Lava heavy", lavaHeavyJson(c)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("INVALID")).andReturn().getResponse().getContentAsString();
		long invalidId = ((Number) JsonPath.read(invalid, "$.id")).longValue();
		authed(put("/api/v1/users/me/decks/" + invalidId + "/defense"), token).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DEFENSE_DECK_INVALID"));

		String valid = createDeck(token, "Valid", deckJson(c, 3, 3)).andReturn().getResponse().getContentAsString();
		long validId = ((Number) JsonPath.read(valid, "$.id")).longValue();
		authed(put("/api/v1/users/me/decks/" + validId + "/defense"), token).andExpect(status().isOk())
				.andExpect(jsonPath("$.isDefenseDeck").value(true));
		authed(get("/api/v1/users/me"), token).andExpect(jsonPath("$.defenseDeckId").value(validId));

		// A traded card leaves the defense deck with more copies than owned: it becomes INVALID (DECK-08).
		jdbc.update("update collection_card set quantity = 1 where card_id = ?", c.lava.get(0));
		authed(get("/api/v1/users/me/decks/" + validId), token)
				.andExpect(jsonPath("$.status").value("INVALID"))
				.andExpect(jsonPath("$.isDefenseDeck").value(true))
				.andExpect(jsonPath("$.validationErrors[?(@.code=='NOT_ENOUGH_COPIES')].cardId").value(c.lava.get(0).intValue()));
		assertThat(jdbc.queryForObject("select status from deck where id = ?", String.class, validId)).isEqualTo("INVALID");

		authed(delete("/api/v1/users/me/decks/" + validId + "/defense"), token).andExpect(status().isNoContent());
		authed(get("/api/v1/users/me"), token).andExpect(jsonPath("$.defenseDeckId").doesNotExist());
		authed(delete("/api/v1/users/me/decks/" + validId), token).andExpect(status().isNoContent());
		authed(get("/api/v1/users/me/decks/" + validId), token).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DECK_NOT_FOUND"));
	}

	@Test
	void savesAtMostTwentyDecks() throws Exception {
		Catalog c = newCatalog();
		String token = newPlayer(c);
		for (int i = 0; i < 20; i++) {
			createDeck(token, "Deck " + i, "[]").andExpect(status().isCreated());
		}
		createDeck(token, "Deck 20", "[]").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DECK_LIMIT_REACHED"));
	}

	@Test
	void decksOfOtherPlayersAreNotFound() throws Exception {
		Catalog c = newCatalog();
		String owner = newPlayer(c);
		String other = newPlayer(c);
		String created = createDeck(owner, "Mine", "[]").andReturn().getResponse().getContentAsString();
		long deckId = ((Number) JsonPath.read(created, "$.id")).longValue();
		authed(get("/api/v1/users/me/decks/" + deckId), other).andExpect(status().isNotFound());
		authed(delete("/api/v1/users/me/decks/" + deckId), other).andExpect(status().isNotFound());
	}

	// ------------------------------------------------------------------ fixtures

	/** Two elemental factions (5 cards each, Lava has 5 more), one inert faction with a Wanderer. */
	private record Catalog(String prefix, int lavaId, int iceId, List<Long> lava, List<Long> ice, long wanderer) {
	}

	private Catalog newCatalog() {
		String p = "t" + UUID.randomUUID().toString().substring(0, 8) + "_";
		int sort = ThreadLocalRandom.current().nextInt(1_000, 1_000_000_000);
		int lavaId = insertFaction(p + "Lava", false);
		int iceId = insertFaction(p + "Ice", false);
		int gasId = insertFaction(p + "Gas", true);
		jdbc.update("insert into faction_advantage (dominant_faction_id, dominated_faction_id, type, value) "
				+ "values (?, ?, 'ATTACK', 3)", lavaId, iceId);
		int common = insertRarity(p + "Common", sort, 12, 3, null, null, true);
		int wandererRarity = insertRarity(p + "Wanderer", sort + 1, 32, 1, 1, 7, false);

		List<Long> lava = new ArrayList<>();
		List<Long> ice = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			lava.add(insertCard(p + "lava_card_" + i, lavaId, common, 5, 7));
		}
		for (int i = 0; i < 5; i++) {
			ice.add(insertCard(p + "ice_card_" + i, iceId, common, 6, 6));
		}
		long wanderer = insertCard(p + "wanderer", gasId, wandererRarity, 16, 16);
		return new Catalog(p, lavaId, iceId, lava, ice, wanderer);
	}

	/** Registers a player who owns 3 copies of the first 6 Lava cards and of every Ice card, and the Wanderer. */
	private String newPlayer(Catalog c) throws Exception {
		String name = "t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"username":"%s","email":"%s@example.com","password":"%s","timezone":"Europe/Rome"}
				""".formatted(name, name, PASSWORD))).andExpect(status().isCreated());
		long playerId = jdbc.queryForObject("select id from player where username = ?", Long.class, name);
		for (int i = 0; i < 6; i++) {
			own(playerId, c.lava.get(i), 3);
		}
		for (int i = 0; i < 5; i++) {
			own(playerId, c.ice.get(i), 3);
		}
		jdbc.update("insert into collection_card (player_id, card_id, quantity, expires_at) "
				+ "values (?, ?, 1, now() + interval '7 days')", playerId, c.wanderer);
		String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"identifier\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	/** 5 Lava cards and 5 Ice cards, with the given copies each. */
	private static String deckJson(Catalog c, int lavaCopies, int iceCopies) {
		StringBuilder json = new StringBuilder("[");
		for (int i = 0; i < 5; i++) {
			json.append("{\"cardId\":").append(c.lava.get(i)).append(",\"quantity\":").append(lavaCopies).append("},");
			json.append("{\"cardId\":").append(c.ice.get(i)).append(",\"quantity\":").append(iceCopies).append("},");
		}
		json.setLength(json.length() - 1);
		return json.append("]").toString();
	}

	/** 6 Lava cards x 3 = 18, three over the faction limit of 15. */
	private static String lavaHeavyJson(Catalog c) {
		StringBuilder json = new StringBuilder("[");
		for (int i = 0; i < 6; i++) {
			json.append("{\"cardId\":").append(c.lava.get(i)).append(",\"quantity\":3},");
		}
		json.setLength(json.length() - 1);
		return json.append("]").toString();
	}

	private ResultActions createDeck(String token, String name, String cardsJson) throws Exception {
		return authed(post("/api/v1/users/me/decks").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"cards\":" + cardsJson + "}"), token);
	}

	private ResultActions authed(MockHttpServletRequestBuilder request, String token) throws Exception {
		return mvc.perform(request.header("Authorization", "Bearer " + token));
	}

	private int insertFaction(String name, boolean inert) {
		return jdbc.queryForObject("insert into faction (name, is_inert) values (?, ?) returning id", Integer.class,
				name, inert);
	}

	private int insertRarity(String name, int sort, int points, int maxCopies, Integer maxPerDeck, Integer days,
			boolean tradeable) {
		return jdbc.queryForObject("insert into rarity (name, sort_order, total_points, max_copies_per_deck, "
				+ "max_per_deck, duration_days, is_tradeable) values (?, ?, ?, ?, ?, ?, ?) returning id",
				Integer.class, name, sort, points, maxCopies, maxPerDeck, days, tradeable);
	}

	private long insertCard(String name, int factionId, int rarityId, int attack, int defense) {
		return jdbc.queryForObject("insert into card (name, faction_id, rarity_id, attack, defense) "
				+ "values (?, ?, ?, ?, ?) returning id", Long.class, name, factionId, rarityId, attack, defense);
	}

	private void own(long playerId, long cardId, int quantity) {
		jdbc.update("insert into collection_card (player_id, card_id, quantity) values (?, ?, ?)", playerId, cardId,
				quantity);
	}
}
