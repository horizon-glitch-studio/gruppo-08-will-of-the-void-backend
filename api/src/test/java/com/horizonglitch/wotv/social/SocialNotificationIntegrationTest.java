package com.horizonglitch.wotv.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.horizonglitch.wotv.collection.CollectionCardId;
import com.horizonglitch.wotv.collection.CollectionExpiryService;
import com.horizonglitch.wotv.notification.NotificationCleanupJob;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class SocialNotificationIntegrationTest {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private CollectionExpiryService wandererExpiry;

	@Autowired
	private NotificationCleanupJob notificationCleanup;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void sendsAcceptsAndRejectsFriendRequestsWithNotifications() throws Exception {
		Player alice = newPlayer();
		Player bob = newPlayer();
		Player carol = newPlayer();

		String sent = mvc.perform(post("/api/v1/friend-requests")
				.header("Authorization", "Bearer " + alice.token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + bob.name + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.direction").value("outgoing"))
				.andReturn().getResponse().getContentAsString();
		long friendshipId = ((Number) JsonPath.read(sent, "$.friendshipId")).longValue();

		assertThat(notificationCount(bob.id, "FRIEND_REQUEST_RECEIVED")).isEqualTo(1);
		mvc.perform(post("/api/v1/friend-requests/" + friendshipId + "/accept")
				.header("Authorization", "Bearer " + bob.token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value(alice.name));
		assertThat(notificationCount(alice.id, "FRIEND_REQUEST_ACCEPTED")).isEqualTo(1);

		String rejected = mvc.perform(post("/api/v1/friend-requests")
				.header("Authorization", "Bearer " + alice.token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + carol.name + "\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long rejectedId = ((Number) JsonPath.read(rejected, "$.friendshipId")).longValue();
		mvc.perform(post("/api/v1/friend-requests/" + rejectedId + "/reject")
				.header("Authorization", "Bearer " + carol.token))
				.andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from friendship where id = ?", Integer.class, rejectedId))
				.isZero();
	}

	@Test
	void listsUnreadAndUpdatesAndDeletesNotifications() throws Exception {
		Player alice = newPlayer();
		long first = jdbc.queryForObject("""
				insert into notification (player_id, type) values (?, 'DECK_INVALID') returning id
				""", Long.class, alice.id);
		jdbc.update("insert into notification (player_id, type, is_read) values (?, 'SEASON_REWARD', true)", alice.id);

		mvc.perform(get("/api/v1/users/me/notifications").header("Authorization", "Bearer " + alice.token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(2));
		mvc.perform(get("/api/v1/users/me/notifications/unread-count")
				.header("Authorization", "Bearer " + alice.token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(1));

		mvc.perform(patch("/api/v1/users/me/notifications/" + first)
				.header("Authorization", "Bearer " + alice.token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"isRead\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isRead").value(true));
		mvc.perform(post("/api/v1/users/me/notifications/read-all")
				.header("Authorization", "Bearer " + alice.token))
				.andExpect(status().isNoContent());
		mvc.perform(delete("/api/v1/users/me/notifications/" + first)
				.header("Authorization", "Bearer " + alice.token))
				.andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from notification where id = ?", Integer.class, first))
				.isZero();
	}

	@Test
	void departedWandererCreatesNotificationAndCleanupAppliesRetention() throws Exception {
		Player alice = newPlayer();
		int factionId = jdbc.queryForObject("insert into faction (name, is_inert) values (?, true) returning id",
				Integer.class, "n_" + UUID.randomUUID());
		long cardId = jdbc.queryForObject("""
				insert into card (name, faction_id, rarity_id, attack, defense)
				select ?, ?, id, 1, 31 from rarity where name = 'Wanderer'
				returning id
				""", Long.class, "n_" + UUID.randomUUID(), factionId);
		jdbc.update("""
				insert into collection_card (player_id, card_id, quantity, acquired_at, expires_at)
				values (?, ?, 1, now() - interval '8 days', now() - interval '1 day')
				""", alice.id, cardId);
		wandererExpiry.departIfDue(new CollectionCardId(alice.id, cardId));
		assertThat(notificationCount(alice.id, "WANDERER_DEPARTED")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from memory_archive where player_id = ? and card_id = ?",
				Integer.class, alice.id, cardId)).isEqualTo(1);

		jdbc.update("delete from notification where player_id = ?", alice.id);
		jdbc.update("insert into notification (player_id, type, created_at) "
				+ "values (?, 'DECK_INVALID', now() - interval '91 days')", alice.id);
		for (int i = 0; i < 501; i++) {
			jdbc.update("insert into notification (player_id, type) values (?, 'DECK_INVALID')", alice.id);
		}
		notificationCleanup.cleanup();
		assertThat(jdbc.queryForObject("select count(*) from notification where player_id = ?",
				Integer.class, alice.id)).isLessThanOrEqualTo(500);
		assertThat(jdbc.queryForObject("select count(*) from notification where player_id = ? "
				+ "and created_at < now() - interval '90 days'", Integer.class, alice.id)).isZero();
	}

	private int notificationCount(long playerId, String type) {
		return jdbc.queryForObject("select count(*) from notification where player_id = ? and type = ?",
				Integer.class, playerId, type);
	}

	private Player newPlayer() throws Exception {
		String name = "s_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		mvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + name + "\",\"email\":\"" + name + "@example.com\","
						+ "\"password\":\"" + PASSWORD + "\",\"timezone\":\"Europe/Rome\"}"))
				.andExpect(status().isCreated());
		long id = jdbc.queryForObject("select id from player where username = ?", Long.class, name);
		jdbc.update("update player set email_verified_at = now() where id = ?", id);
		String body = mvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"identifier\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andReturn().getResponse().getContentAsString();
		return new Player(id, name, JsonPath.read(body, "$.accessToken"));
	}

	private record Player(long id, String name, String token) {
	}
}
