package com.horizonglitch.wotv.battle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Players, cards and decks for the battle and matchmaking tests, against the real schema with the factions and
 * rarities of V2. A strong deck (ATK 10, DEF 10) always beats a weak one (ATK 1, DEF 1) by threshold: every
 * clash defeats the weak creature in one round with at least 9 excess damage, whatever the shuffle.
 */
public final class BattleFixtures {

	public static final String PASSWORD = "Password123!";

	private final MockMvc mvc;
	private final JdbcTemplate jdbc;

	public BattleFixtures(MockMvc mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.jdbc = jdbc;
	}

	/** Ten Captive cards each: three copies of all of them make a valid 30-card deck. */
	public record Cards(List<Long> strong, List<Long> weak) {
	}

	public record Player(long id, String name, String token) {
	}

	public Cards newCards() {
		String prefix = "b" + UUID.randomUUID().toString().substring(0, 8) + "_";
		List<Long> strong = new ArrayList<>();
		List<Long> weak = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			strong.add(card(prefix + "lava_" + i, "Lava", 10, 10));
			strong.add(card(prefix + "ice_" + i, "Ice", 10, 10));
			weak.add(card(prefix + "metal_" + i, "Metal", 1, 1));
			weak.add(card(prefix + "matte_" + i, "Matte", 1, 1));
		}
		return new Cards(strong, weak);
	}

	private long card(String name, String faction, int attack, int defense) {
		return jdbc.queryForObject("insert into card (name, faction_id, rarity_id, attack, defense) "
				+ "select ?, f.id, r.id, ?, ? from faction f, rarity r where f.name = ? and r.name = 'Captive' "
				+ "returning id", Long.class, name, attack, defense, faction);
	}

	/** A verified player who has just logged in, so they count as active. */
	public Player newPlayer() throws Exception {
		String name = "b_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"username":"%s","email":"%s@example.com","password":"%s","timezone":"Europe/Rome"}
				""".formatted(name, name, PASSWORD))).andExpect(status().isCreated());
		long id = jdbc.queryForObject("select id from player where username = ?", Long.class, name);
		jdbc.update("update player set email_verified_at = now() where id = ?", id);
		String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"identifier\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andReturn().getResponse().getContentAsString();
		return new Player(id, name, JsonPath.read(body, "$.accessToken"));
	}

	/** Gives the player three copies of the cards and saves a VALID deck with them. */
	public long deck(Player player, String name, List<Long> cards) throws Exception {
		for (long cardId : cards) {
			jdbc.update("insert into collection_card (player_id, card_id, quantity) values (?, ?, 3) "
					+ "on conflict (player_id, card_id) do nothing", player.id, cardId);
		}
		return saveDeck(player, name, cards, 3);
	}

	public long saveDeck(Player player, String name, List<Long> cards, int copies) throws Exception {
		String body = authed(post("/api/v1/users/me/decks").contentType(MediaType.APPLICATION_JSON)
				.content(deckJson(name, cards, copies)), player)
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	public static String deckJson(String name, List<Long> cards, int copies) {
		return "{\"name\":\"" + name + "\",\"cards\":[" + cards.stream()
				.map(id -> "{\"cardId\":" + id + ",\"quantity\":" + copies + "}").collect(Collectors.joining(",")) + "]}";
	}

	/** A VALID deck set as the defense deck. */
	public long defenseDeck(Player player, List<Long> cards) throws Exception {
		long deckId = deck(player, "Defense", cards);
		authed(put("/api/v1/users/me/decks/" + deckId + "/defense"), player).andExpect(status().isOk());
		return deckId;
	}

	public void befriend(Player a, Player b) {
		jdbc.update("insert into friendship (requester_id, addressee_id, status, accepted_at) "
				+ "values (?, ?, 'ACCEPTED', now())", a.id, b.id);
	}

	/** Replaces the saved opponent list of the player for the mode, without any battle played. */
	public void giveList(Player player, String mode, Player... opponents) {
		jdbc.update("insert into opponent_list (player_id, mode, generated_at) values (?, ?, now()) "
				+ "on conflict (player_id, mode) do update set generated_at = now()", player.id, mode);
		jdbc.update("delete from opponent_list_entry where player_id = ? and mode = ?", player.id, mode);
		for (int i = 0; i < opponents.length; i++) {
			jdbc.update("insert into opponent_list_entry (player_id, mode, position, opponent_id, "
					+ "trophies_at_generation) values (?, ?, ?, ?, 0)", player.id, mode, i + 1, opponents[i].id);
		}
	}

	public ResultActions attack(Player attacker, Player opponent, String mode, long deckId) throws Exception {
		return authed(post("/api/v1/matchmaking/opponents/" + opponent.id + "/battle")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"mode\":\"" + mode + "\",\"deckId\":" + deckId + "}"), attacker);
	}

	public ResultActions friendBattle(Player challenger, Player opponent, long deckId) throws Exception {
		return authed(post("/api/v1/battles/friend").contentType(MediaType.APPLICATION_JSON)
				.content("{\"opponentUsername\":\"" + opponent.name + "\",\"deckId\":" + deckId + "}"), challenger);
	}

	public ResultActions withDeck(String route, Player player, long deckId) throws Exception {
		return authed(post(route).contentType(MediaType.APPLICATION_JSON).content("{\"deckId\":" + deckId + "}"),
				player);
	}

	public ResultActions authed(MockHttpServletRequestBuilder request, Player player) throws Exception {
		return mvc.perform(request.header("Authorization", "Bearer " + player.token));
	}

	public int notifications(Player player, String type) {
		return jdbc.queryForObject("select count(*) from notification where player_id = ? and type = ?",
				Integer.class, player.id, type);
	}

	public int trophies(Player player) {
		return jdbc.queryForObject("select trophies from player where id = ?", Integer.class, player.id);
	}

	public void setTrophies(Player player, int trophies) {
		jdbc.update("update player set trophies = ? where id = ?", trophies, player.id);
	}

	public static String body(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString();
	}

	public static long id(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(body(result), "$.id")).longValue();
	}

	/** Checks a PLAYED battle won by the challenger by threshold and returns its id. */
	public static long wonByChallenger(ResultActions result, int threshold) throws Exception {
		String body = body(result.andExpect(status().isCreated()));
		assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("PLAYED");
		assertThat(JsonPath.<String>read(body, "$.outcome")).isEqualTo("CHALLENGER_WIN");
		assertThat(JsonPath.<Integer>read(body, "$.challengerEnergy")).isGreaterThanOrEqualTo(threshold);
		assertThat(JsonPath.<Integer>read(body, "$.opponentEnergy")).isZero();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}
}
