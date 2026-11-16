package com.horizonglitch.wotv.pack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/**
 * Buying and opening packs against the real schema. The six rarities of the rules are created once and shared;
 * every test creates its own cards, pack types and player.
 */
@SpringBootTest
class PackIntegrationTest {

	private static final String PASSWORD = "Password123!";
	private static final String[] RARITIES = {"Captive", "Conscript", "Soldier", "Champion", "Elect", "Wanderer"};

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		for (int i = 0; i < RARITIES.length; i++) {
			boolean wanderer = RARITIES[i].equals("Wanderer");
			jdbc.update("insert into rarity (name, sort_order, total_points, max_copies_per_deck, max_per_deck, "
					+ "duration_days, is_tradeable) values (?, ?, ?, 3, null, ?, ?) on conflict (name) do nothing",
					RARITIES[i], i + 1, 12 + 4 * i, wanderer ? 7 : null, !wanderer);
		}
	}

	@Test
	void listsThePacksOnSaleWithOddsAndGuarantees() throws Exception {
		Shop shop = newShop();
		String token = newPlayer();

		String body = authed(get("/api/v1/packs"), token).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<Integer> ids = JsonPath.read(body, "$[*].id");
		assertThat(ids).contains(shop.standard, shop.event).doesNotContain(shop.ended);

		authed(get("/api/v1/packs/" + shop.event), token).andExpect(status().isOk())
				.andExpect(jsonPath("$.kind").value("EVENT"))
				.andExpect(jsonPath("$.available").value(true))
				.andExpect(jsonPath("$.slots.length()").value(5))
				.andExpect(jsonPath("$.slots[4].odds.length()").value(4))
				.andExpect(jsonPath("$.guarantees[?(@.type=='WANDERER')].everyPacks").value(50))
				.andExpect(jsonPath("$.eventCards[0].id").value((int) shop.wanderer));
		authed(get("/api/v1/packs/" + shop.ended), token).andExpect(jsonPath("$.available").value(false));
		authed(get("/api/v1/packs/999999"), token).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PACK_NOT_FOUND"));
	}

	@Test
	void buysAndOpensAPackInOneTransaction() throws Exception {
		Shop shop = newShop();
		String token = newPlayer();
		long playerId = playerId(token);
		int before = balance(playerId);

		String body = open(token, shop.standard).andExpect(status().isCreated())
				.andExpect(jsonPath("$.pack.id").value(shop.standard))
				.andExpect(jsonPath("$.shardsSpent").value(300))
				.andExpect(jsonPath("$.shardBalance").value(before - 300))
				.andReturn().getResponse().getContentAsString();
		List<Integer> quantities = JsonPath.read(body, "$.cards[*].quantity");
		assertThat(quantities.stream().mapToInt(Integer::intValue).sum()).isEqualTo(5);

		long openingId = ((Number) JsonPath.read(body, "$.id")).longValue();
		assertThat(jdbc.queryForObject("select amount from shard_transaction where pack_opening_id = ?",
				Integer.class, openingId)).isEqualTo(-300);
		assertThat(jdbc.queryForObject("select coalesce(sum(quantity), 0) from collection_card where player_id = ?",
				Integer.class, playerId)).isEqualTo(5);
		assertThat(jdbc.queryForObject("select packs_without_champion + standard_packs_without_elect "
				+ "from pack_guarantee where player_id = ?", Integer.class, playerId)).isBetween(0, 2);
	}

	@Test
	void refusesAPackWithoutEnoughShardsAndChangesNothing() throws Exception {
		Shop shop = newShop();
		String token = newPlayer();
		long playerId = playerId(token);
		jdbc.update("update player set shard_balance = 120 where id = ?", playerId);

		open(token, shop.standard).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_SHARDS"))
				.andExpect(jsonPath("$.details.shardBalance").value(120))
				.andExpect(jsonPath("$.details.shardCost").value(300));
		assertThat(balance(playerId)).isEqualTo(120);
		assertThat(jdbc.queryForObject("select count(*) from pack_opening where player_id = ?", Integer.class,
				playerId)).isZero();

		open(token, shop.ended).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PACK_NOT_AVAILABLE"));
	}

	@Test
	void theChampionGuaranteeGivesARareCardOnTheTenthPack() throws Exception {
		Shop shop = newShop();
		String token = newPlayer();
		long playerId = playerId(token);
		jdbc.update("update pack_guarantee set packs_without_champion = 9 where player_id = ?", playerId);

		String body = open(token, shop.standard).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		List<String> rarities = JsonPath.read(body, "$.cards[*].card.rarity.name");
		assertThat(rarities).containsAnyOf("Champion", "Elect");
		assertThat(jdbc.queryForObject("select packs_without_champion from pack_guarantee where player_id = ?",
				Integer.class, playerId)).isZero();
	}

	@Test
	void aWandererFoundAgainExtendsItsStay() throws Exception {
		Shop shop = newShop();
		String token = newPlayer();
		long playerId = playerId(token);

		jdbc.update("update pack_guarantee set event_packs_without_wanderer = 49 where player_id = ?", playerId);
		open(token, shop.event).andExpect(status().isCreated())
				.andExpect(jsonPath("$.cards[?(@.card.id == " + shop.wanderer + ")].isNew").value(true));
		Instant first = jdbc.queryForObject("select expires_at from collection_card where player_id = ? and card_id = ?",
				java.sql.Timestamp.class, playerId, shop.wanderer).toInstant();
		assertThat(first).isBetween(Instant.now().plus(7, ChronoUnit.DAYS).minus(1, ChronoUnit.MINUTES),
				Instant.now().plus(7, ChronoUnit.DAYS).plus(1, ChronoUnit.MINUTES));

		jdbc.update("update player set shard_balance = 300 where id = ?", playerId);
		jdbc.update("update pack_guarantee set event_packs_without_wanderer = 49 where player_id = ?", playerId);
		open(token, shop.event).andExpect(status().isCreated())
				.andExpect(jsonPath("$.cards[?(@.card.id == " + shop.wanderer + ")].isNew").value(false));
		assertThat(jdbc.queryForObject("select quantity from collection_card where player_id = ? and card_id = ?",
				Integer.class, playerId, shop.wanderer)).isEqualTo(1);
		Instant second = jdbc.queryForObject("select expires_at from collection_card where player_id = ? and card_id = ?",
				java.sql.Timestamp.class, playerId, shop.wanderer).toInstant();
		assertThat(second).isEqualTo(first.plus(7, ChronoUnit.DAYS));
	}

	@Test
	void keepsTheHistoryOfOpeningsAndShards() throws Exception {
		Shop shop = newShop();
		String token = newPlayer();
		long playerId = playerId(token);
		jdbc.update("update player set shard_balance = 600 where id = ?", playerId);
		open(token, shop.standard).andExpect(status().isCreated());
		String last = open(token, shop.standard).andReturn().getResponse().getContentAsString();
		long lastId = ((Number) JsonPath.read(last, "$.id")).longValue();

		authed(get("/api/v1/users/me/pack-openings"), token).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(2))
				.andExpect(jsonPath("$.items[0].id").value((int) lastId))
				.andExpect(jsonPath("$.items[0].totalCards").value(5));
		authed(get("/api/v1/users/me/pack-openings/" + lastId), token).andExpect(status().isOk())
				.andExpect(jsonPath("$.pack.id").value(shop.standard));
		authed(get("/api/v1/users/me/pack-openings/999999999"), token).andExpect(status().isNotFound());

		authed(get("/api/v1/users/me/shard-transactions"), token).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].reason").value("PACK_PURCHASE"))
				.andExpect(jsonPath("$.items[0].amount").value(-300))
				.andExpect(jsonPath("$.items[0].packOpeningId").value((int) lastId))
				.andExpect(jsonPath("$.items[?(@.reason=='REGISTRATION_BONUS')]").isNotEmpty());
	}

	// ------------------------------------------------------------------ fixtures

	/** A standard pack, an event pack on sale with one Wanderer, and an event that has ended. */
	private record Shop(int standard, int event, int ended, long wanderer) {
	}

	private Shop newShop() {
		String p = "t" + UUID.randomUUID().toString().substring(0, 8) + "_";
		int faction = jdbc.queryForObject("insert into faction (name) values (?) returning id", Integer.class, p + "F");
		int gas = jdbc.queryForObject("insert into faction (name, is_inert) values (?, true) returning id",
				Integer.class, p + "Gas");
		for (String rarity : RARITIES) {
			if (!rarity.equals("Wanderer")) {
				card(p + rarity, faction, rarity);
			}
		}
		long wanderer = card(p + "Wanderer", gas, "Wanderer");

		int standard = packType(p + "Standard", "STANDARD", null, null);
		int event = packType(p + "Event", "EVENT", Instant.now().minus(1, ChronoUnit.HOURS),
				Instant.now().plus(1, ChronoUnit.HOURS));
		int ended = packType(p + "Ended", "EVENT", Instant.now().minus(3, ChronoUnit.DAYS),
				Instant.now().minus(1, ChronoUnit.DAYS));
		for (int pack : List.of(standard, event, ended)) {
			boolean isEvent = pack != standard;
			for (int slot = 1; slot <= 3; slot++) {
				odds(pack, slot, "Captive", "70", "Conscript", "30");
			}
			odds(pack, 4, "Conscript", "75", "Soldier", "25");
			jdbc.update("insert into pack_slot (pack_type_id, position) values (?, 5)", pack);
			slotRarity(pack, 5, "Soldier", "84");
			slotRarity(pack, 5, "Champion", isEvent ? "11" : "12.5");
			slotRarity(pack, 5, "Elect", isEvent ? "1.5" : "3.5");
			if (isEvent) {
				slotRarity(pack, 5, "Wanderer", "3.5");
				jdbc.update("insert into event_pack_card (pack_type_id, card_id) values (?, ?)", pack, wanderer);
			}
		}
		return new Shop(standard, event, ended, wanderer);
	}

	private long card(String name, int factionId, String rarity) {
		return jdbc.queryForObject("insert into card (name, faction_id, rarity_id, attack, defense) "
				+ "select ?, ?, id, 1, total_points - 1 from rarity where name = ? returning id", Long.class,
				name, factionId, rarity);
	}

	private int packType(String name, String kind, Instant from, Instant until) {
		return jdbc.queryForObject("insert into pack_type (name, kind, shard_cost, available_from, available_until) "
				+ "values (?, ?, 300, ?, ?) returning id", Integer.class, name, kind,
				from == null ? null : java.sql.Timestamp.from(from), until == null ? null : java.sql.Timestamp.from(until));
	}

	private void odds(int pack, int position, String a, String pa, String b, String pb) {
		jdbc.update("insert into pack_slot (pack_type_id, position) values (?, ?)", pack, position);
		slotRarity(pack, position, a, pa);
		slotRarity(pack, position, b, pb);
	}

	private void slotRarity(int pack, int position, String rarity, String probability) {
		jdbc.update("insert into pack_slot_rarity (pack_type_id, position, rarity_id, probability) "
				+ "select ?, ?, id, ?::numeric from rarity where name = ?", pack, position, probability, rarity);
	}

	/** A new player: the registration bonus pays exactly one pack. */
	private String newPlayer() throws Exception {
		String name = "t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"username":"%s","email":"%s@example.com","password":"%s","timezone":"Europe/Rome"}
				""".formatted(name, name, PASSWORD))).andExpect(status().isCreated());
		jdbc.update("update player set shard_balance = 300 where username = ?", name);
		String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"identifier\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andReturn().getResponse().getContentAsString();
		jdbc.update("update player set shard_balance = 300 where username = ?", name);
		return JsonPath.read(body, "$.accessToken");
	}

	private long playerId(String token) throws Exception {
		String me = authed(get("/api/v1/users/me"), token).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(me, "$.id")).longValue();
	}

	private int balance(long playerId) {
		return jdbc.queryForObject("select shard_balance from player where id = ?", Integer.class, playerId);
	}

	private ResultActions open(String token, int packTypeId) throws Exception {
		return authed(post("/api/v1/packs/" + packTypeId + "/open"), token);
	}

	private ResultActions authed(MockHttpServletRequestBuilder request, String token) throws Exception {
		return mvc.perform(request.header("Authorization", "Bearer " + token));
	}
}
