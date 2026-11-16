package com.horizonglitch.wotv.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

/**
 * Trades against the real schema, with the rarities of V2. Every test creates its own cards and players; each
 * player owns 40 copies of a filler card, so the 30-card minimum is not in the way unless a test lowers it.
 */
@SpringBootTest
class TradeIntegrationTest {

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
	void proposesATradeAndNotifiesTheRecipient() throws Exception {
		Cards c = newCards();
		Player alice = newPlayer(c);
		Player bob = newPlayer(c);

		propose(alice, bob, offer(c.soldier, 1), request(c.captive, 2)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.proposer.username").value(alice.name))
				.andExpect(jsonPath("$.recipient.username").value(bob.name))
				.andExpect(jsonPath("$.offeredCards[0].card.id").value((int) c.soldier))
				.andExpect(jsonPath("$.requestedCards[0].quantity").value(2))
				.andExpect(jsonPath("$.closedAt").doesNotExist());

		authed(get("/api/v1/trades").param("direction", "incoming"), bob).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(1));
		authed(get("/api/v1/trades").param("direction", "incoming"), alice).andExpect(jsonPath("$.totalItems").value(0));
		assertThat(notifications(bob, "TRADE_RECEIVED")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select extract(epoch from expires_at - created_at) / 3600 from trade "
				+ "where proposer_id = ?", Double.class, alice.id)).isBetween(47.9, 48.1);
	}

	@Test
	void refusesInvalidProposals() throws Exception {
		Cards c = newCards();
		Player alice = newPlayer(c);
		Player bob = newPlayer(c);
		Player unverified = newPlayer(c);
		jdbc.update("update player set email_verified_at = null where id = ?", unverified.id);

		propose(unverified, bob, offer(c.soldier, 1), "[]").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
		mvc.perform(post("/api/v1/trades").header("Authorization", "Bearer " + alice.token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"recipientUsername\":\"nobody_" + bob.name + "\",\"offeredCards\":" + offer(c.soldier, 1) + "}"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
		propose(alice, alice, offer(c.soldier, 1), "[]").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("TRADE_WITH_SELF"));
		propose(alice, bob, "[]", "[]").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("TRADE_INVALID_CARDS"))
				.andExpect(jsonPath("$.fieldErrors[0].code").value("TRADE_EMPTY"));
		propose(alice, bob, "[" + card(c.soldier, 1) + "," + card(c.soldier, 1) + "]", "[]")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].code").value("CARD_DUPLICATED"));
		propose(alice, bob, offer(c.wanderer, 1), "[]").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("CARD_NOT_TRADEABLE"));
		propose(alice, bob, offer(c.soldier, 0), "[]").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("QUANTITY_INVALID"));

		propose(alice, bob, offer(c.soldier, 1), "[]").andExpect(status().isCreated());
		propose(bob, alice, offer(c.captive, 1), "[]").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TRADE_ALREADY_PENDING"));
	}

	@Test
	void acceptingMovesTheCardsAndRechecksTheDecks() throws Exception {
		Cards c = newCards();
		Player alice = newPlayer(c);
		Player bob = newPlayer(c);
		own(alice, c.soldier, 2);
		long deckId = createDeck(alice, c.soldier, 2);

		long tradeId = tradeId(propose(alice, bob, offer(c.soldier, 2), request(c.captive, 3)));
		authed(post("/api/v1/trades/" + tradeId + "/accept"), alice).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TRADE_FORBIDDEN"));
		authed(post("/api/v1/trades/" + tradeId + "/accept"), bob).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCEPTED"))
				.andExpect(jsonPath("$.closedAt").isNotEmpty());

		assertThat(quantity(alice, c.soldier)).isZero();
		assertThat(quantity(bob, c.soldier)).isEqualTo(2);
		assertThat(quantity(alice, c.captive)).isEqualTo(6);
		assertThat(quantity(bob, c.captive)).isZero();
		assertThat(jdbc.queryForObject("select status from deck where id = ?", String.class, deckId)).isEqualTo("INVALID");
		assertThat(notifications(alice, "TRADE_ACCEPTED")).isEqualTo(1);
		assertThat(notifications(alice, "DECK_INVALID")).isEqualTo(1);

		authed(post("/api/v1/trades/" + tradeId + "/accept"), bob).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TRADE_NOT_PENDING"))
				.andExpect(jsonPath("$.details.status").value("ACCEPTED"));
	}

	@Test
	void failsWhenACardIsNoLongerOwned() throws Exception {
		Cards c = newCards();
		Player alice = newPlayer(c);
		Player bob = newPlayer(c);
		Player carol = newPlayer(c);
		own(alice, c.soldier, 1);

		long toBob = tradeId(propose(alice, bob, offer(c.soldier, 1), "[]"));
		long toCarol = tradeId(propose(alice, carol, offer(c.soldier, 1), "[]"));
		authed(post("/api/v1/trades/" + toBob + "/accept"), bob).andExpect(status().isOk());

		authed(post("/api/v1/trades/" + toCarol + "/accept"), carol).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TRADE_FAILED"));
		authed(get("/api/v1/trades/" + toCarol), carol).andExpect(jsonPath("$.status").value("FAILED"));
		assertThat(quantity(carol, c.soldier)).isZero();
		assertThat(quantity(bob, c.soldier)).isEqualTo(1);
		assertThat(notifications(alice, "TRADE_FAILED")).isEqualTo(1);
	}

	@Test
	void nobodyGoesBelowThirtyCards() throws Exception {
		Cards c = newCards();
		Player alice = newPlayer(c);
		Player bob = newPlayer(c);
		jdbc.update("update collection_card set quantity = 27 where player_id = ? and card_id = ?", bob.id, c.filler);

		long tradeId = tradeId(propose(alice, bob, "[]", request(c.captive, 1)));
		authed(post("/api/v1/trades/" + tradeId + "/accept"), bob).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TRADE_MIN_COLLECTION"));
		authed(get("/api/v1/trades/" + tradeId), bob).andExpect(jsonPath("$.status").value("PENDING"));
		assertThat(quantity(bob, c.captive)).isEqualTo(3);
	}

	@Test
	void rejectsCancelsAndHidesTradesFromOthers() throws Exception {
		Cards c = newCards();
		Player alice = newPlayer(c);
		Player bob = newPlayer(c);
		Player carol = newPlayer(c);

		long rejected = tradeId(propose(alice, bob, offer(c.soldier, 1), "[]"));
		authed(get("/api/v1/trades/" + rejected), carol).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TRADE_FORBIDDEN"));
		authed(post("/api/v1/trades/" + rejected + "/cancel"), bob).andExpect(status().isForbidden());
		authed(post("/api/v1/trades/" + rejected + "/reject"), bob).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"));
		assertThat(notifications(alice, "TRADE_REJECTED")).isEqualTo(1);

		long cancelled = tradeId(propose(alice, bob, offer(c.soldier, 1), "[]"));
		authed(post("/api/v1/trades/" + cancelled + "/cancel"), alice).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		assertThat(notifications(bob, "TRADE_CANCELLED")).isEqualTo(1);
		authed(post("/api/v1/trades/" + cancelled + "/reject"), bob).andExpect(status().isConflict())
				.andExpect(jsonPath("$.details.status").value("CANCELLED"));
		authed(get("/api/v1/trades/999999999"), bob).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TRADE_NOT_FOUND"));
	}

	@Test
	void anExpiredTradeCannotBeAcceptedAndFreesThePair() throws Exception {
		Cards c = newCards();
		Player alice = newPlayer(c);
		Player bob = newPlayer(c);
		long tradeId = tradeId(propose(alice, bob, offer(c.soldier, 1), "[]"));
		jdbc.update("update trade set created_at = now() - interval '3 days', expires_at = now() - interval '1 day' "
				+ "where id = ?", tradeId);

		authed(post("/api/v1/trades/" + tradeId + "/accept"), bob).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TRADE_NOT_PENDING"))
				.andExpect(jsonPath("$.details.status").value("EXPIRED"));
		authed(get("/api/v1/trades/" + tradeId), alice).andExpect(jsonPath("$.status").value("EXPIRED"));
		assertThat(notifications(alice, "TRADE_EXPIRED")).isEqualTo(1);
		propose(alice, bob, offer(c.soldier, 1), "[]").andExpect(status().isCreated());
	}

	@Test
	void deletingTheAccountCancelsItsPendingTrades() throws Exception {
		Cards c = newCards();
		Player alice = newPlayer(c);
		Player bob = newPlayer(c);
		long tradeId = tradeId(propose(alice, bob, offer(c.soldier, 1), "[]"));

		authed(delete("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"" + PASSWORD + "\"}"), alice).andExpect(status().isNoContent());
		authed(get("/api/v1/trades/" + tradeId), bob).andExpect(jsonPath("$.status").value("CANCELLED"));
		assertThat(notifications(bob, "TRADE_CANCELLED")).isEqualTo(1);
	}

	// ------------------------------------------------------------------ fixtures

	private record Cards(long filler, long captive, long soldier, long wanderer) {
	}

	private record Player(long id, String name, String token) {
	}

	private Cards newCards() {
		String p = "t" + UUID.randomUUID().toString().substring(0, 8) + "_";
		int faction = jdbc.queryForObject("insert into faction (name) values (?) returning id", Integer.class, p + "F");
		int gas = jdbc.queryForObject("insert into faction (name, is_inert) values (?, true) returning id", Integer.class,
				p + "Gas");
		return new Cards(card(p + "filler", faction, "Captive"), card(p + "captive", faction, "Captive"),
				card(p + "soldier", faction, "Soldier"), card(p + "wanderer", gas, "Wanderer"));
	}

	private long card(String name, int factionId, String rarity) {
		return jdbc.queryForObject("insert into card (name, faction_id, rarity_id, attack, defense) "
				+ "select ?, ?, id, 1, total_points - 1 from rarity where name = ? returning id", Long.class,
				name, factionId, rarity);
	}

	/** A verified player with 40 filler cards, 3 Captive cards and 1 Wanderer on board. */
	private Player newPlayer(Cards c) throws Exception {
		String name = "t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"username":"%s","email":"%s@example.com","password":"%s","timezone":"Europe/Rome"}
				""".formatted(name, name, PASSWORD))).andExpect(status().isCreated());
		long id = jdbc.queryForObject("select id from player where username = ?", Long.class, name);
		jdbc.update("update player set email_verified_at = now() where id = ?", id);
		jdbc.update("insert into collection_card (player_id, card_id, quantity) values (?, ?, 40), (?, ?, 3)",
				id, c.filler, id, c.captive);
		jdbc.update("insert into collection_card (player_id, card_id, quantity, expires_at) "
				+ "values (?, ?, 1, now() + interval '7 days')", id, c.wanderer);
		String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"identifier\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andReturn().getResponse().getContentAsString();
		return new Player(id, name, JsonPath.read(body, "$.accessToken"));
	}

	private void own(Player player, long cardId, int quantity) {
		jdbc.update("insert into collection_card (player_id, card_id, quantity) values (?, ?, ?)", player.id, cardId,
				quantity);
	}

	private long createDeck(Player player, long cardId, int quantity) throws Exception {
		String body = authed(post("/api/v1/users/me/decks").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Deck\",\"cards\":[" + card(cardId, quantity) + "]}"), player)
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private ResultActions propose(Player from, Player to, String offered, String requested) throws Exception {
		return authed(post("/api/v1/trades").contentType(MediaType.APPLICATION_JSON)
				.content("{\"recipientUsername\":\"" + to.name + "\",\"offeredCards\":" + offered
						+ ",\"requestedCards\":" + requested + "}"), from);
	}

	private static long tradeId(ResultActions result) throws Exception {
		String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private static String offer(long cardId, int quantity) {
		return "[" + card(cardId, quantity) + "]";
	}

	private static String request(long cardId, int quantity) {
		return "[" + card(cardId, quantity) + "]";
	}

	private static String card(long cardId, int quantity) {
		return "{\"cardId\":" + cardId + ",\"quantity\":" + quantity + "}";
	}

	private int quantity(Player player, long cardId) {
		return jdbc.queryForObject("select coalesce(sum(quantity), 0) from collection_card where player_id = ? "
				+ "and card_id = ?", Integer.class, player.id, cardId);
	}

	private int notifications(Player player, String type) {
		return jdbc.queryForObject("select count(*) from notification where player_id = ? and type = ?",
				Integer.class, player.id, type);
	}

	private ResultActions authed(MockHttpServletRequestBuilder request, Player player) throws Exception {
		return mvc.perform(request.header("Authorization", "Bearer " + player.token));
	}
}
