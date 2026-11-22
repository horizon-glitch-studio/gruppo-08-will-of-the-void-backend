package com.horizonglitch.wotv.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** Profile routes against the real schema. Every test registers its own players. */
@SpringBootTest
class ProfileIntegrationTest {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlayerRepository players;

	@Autowired
	private PlayerActivityService activity;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void showsThePrivateProfile() throws Exception {
		Session s = newPlayer();
		jdbc.update("update player set trophies = 520 where username = ?", s.username);

		authed(get("/api/v1/users/me"), s).andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value(s.username))
				.andExpect(jsonPath("$.email").value(s.username + "@example.com"))
				.andExpect(jsonPath("$.emailVerified").value(false))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.avatar").doesNotExist())
				.andExpect(jsonPath("$.trophies").value(520))
				.andExpect(jsonPath("$.league").value("ORBIT_3"))
				.andExpect(jsonPath("$.defenseDeckId").doesNotExist())
				.andExpect(jsonPath("$.timezone").value("Europe/Rome"))
				.andExpect(jsonPath("$.inviteCode").isNotEmpty())
				.andExpect(jsonPath("$.stats.matchesPlayed").value(0))
				.andExpect(jsonPath("$.stats.winRate").value(0.0))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());

		mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
	}

	@Test
	void changesTheTimeZoneAtMostOnceEvery30Days() throws Exception {
		Session s = newPlayer();
		patchMe(s, "{\"timezone\":\"Europe/London\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.timezone").value("Europe/London"));
		patchMe(s, "{\"timezone\":\"Europe/Paris\"}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TIMEZONE_CHANGE_TOO_SOON"));
		patchMe(s, "{\"timezone\":\"Europe/London\"}").andExpect(status().isOk());

		jdbc.update("update player set timezone_changed_at = now() - interval '31 days' where username = ?", s.username);
		patchMe(s, "{\"timezone\":\"Europe/Paris\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.timezone").value("Europe/Paris"));

		patchMe(s, "{\"timezone\":\"+01:00\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("TIMEZONE_INVALID"));
	}

	@Test
	void choosesOnlyAnActiveAvatar() throws Exception {
		Session s = newPlayer();
		String key = "av_" + s.username;
		jdbc.update("insert into avatar (name, image_url, sort_order) values (?, '/avatars/test.webp', "
				+ "(select coalesce(max(sort_order), 0) + 1 from avatar))", key);

		patchMe(s, "{\"avatar\":\"" + key + "\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.avatar").value(key));
		patchMe(s, "{\"avatar\":\"missing_avatar\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("avatar"))
				.andExpect(jsonPath("$.fieldErrors[0].code").value("AVATAR_INVALID"));

		jdbc.update("update avatar set is_active = false where name = ?", key);
		authed(get("/api/v1/users/me"), s).andExpect(jsonPath("$.avatar").value(key));
		patchMe(s, "{\"avatar\":\"" + key + "\"}").andExpect(status().isBadRequest());
	}

	@Test
	void updatesOnlyTheSettingsThatAreSent() throws Exception {
		Session s = newPlayer();
		authed(get("/api/v1/users/me/settings"), s).andExpect(status().isOk())
				.andExpect(jsonPath("$.reduceMotion").value(false))
				.andExpect(jsonPath("$.reduceTransparency").value(false))
				.andExpect(jsonPath("$.showTextLabels").value(false));

		authed(patch("/api/v1/users/me/settings").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reduceMotion\":true}"), s).andExpect(status().isOk())
				.andExpect(jsonPath("$.reduceMotion").value(true))
				.andExpect(jsonPath("$.showTextLabels").value(false));
		authed(patch("/api/v1/users/me/settings").contentType(MediaType.APPLICATION_JSON)
				.content("{\"showTextLabels\":true}"), s).andExpect(status().isOk())
				.andExpect(jsonPath("$.reduceMotion").value(true))
				.andExpect(jsonPath("$.showTextLabels").value(true));
	}

	@Test
	void stalePlayerSaveDoesNotOverwriteDatabaseTrackedActivity() throws Exception {
		Session s = newPlayer();
		Player stale = players.findActiveByUsername(s.username).orElseThrow();
		Long playerId = stale.getId();

		jdbc.update("update player set last_active_at = now() - interval '8 days', comeback_eligible = false where id = ?",
				playerId);
		activity.record(playerId);

		stale.setTimezone("Europe/London");
		players.save(stale);

		assertThat(jdbc.queryForObject("select last_active_at > now() - interval '1 hour' from player where id = ?",
				Boolean.class, playerId)).isTrue();
		assertThat(jdbc.queryForObject("select comeback_eligible from player where id = ?", Boolean.class,
				playerId)).isTrue();
		assertThat(jdbc.queryForObject("select timezone from player where id = ?", String.class, playerId))
				.isEqualTo("Europe/London");
	}

	@Test
	void changesThePasswordAndLogsOutTheOtherDevices() throws Exception {
		Session s = newPlayer();
		changePassword(s, "WrongPassword1", "NewPassword456!").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		changePassword(s, PASSWORD, "short").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("PASSWORD_TOO_SHORT"));
		changePassword(s, PASSWORD, "NewPassword456!").andExpect(status().isNoContent());

		login(s.username, PASSWORD).andExpect(status().isUnauthorized());
		login(s.username, "NewPassword456!").andExpect(status().isOk());
		mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + s.refreshToken + "\"}")).andExpect(status().isUnauthorized());
	}

	@Test
	void deletesTheAccountAndAnonymisesIt() throws Exception {
		Session s = newPlayer();
		deleteMe(s, "WrongPassword1").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		deleteMe(s, PASSWORD).andExpect(status().isNoContent());

		login(s.username, PASSWORD).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/users/" + s.username)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
		authed(get("/api/v1/users/me"), s).andExpect(status().isUnauthorized());
		assertThat(jdbc.queryForObject("select count(*) from player where username = ? or email = ?", Integer.class,
				s.username, s.username + "@example.com")).isZero();

		Session again = registerAndLogin(s.username);
		assertThat(again.accessToken).isNotEmpty();
	}

	@Test
	void showsThePublicProfileWithStatsAndNoPrivateData() throws Exception {
		Session player = newPlayer();
		Session rival = newPlayer();
		insertPlayedBattle(player, rival, "CHALLENGER_WIN");
		insertPlayedBattle(rival, player, "DRAW");
		insertPlayedBattle(rival, player, "CHALLENGER_WIN");

		mvc.perform(get("/api/v1/users/" + player.username.toUpperCase())).andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value(player.username))
				.andExpect(jsonPath("$.league").value("ORBIT_1"))
				.andExpect(jsonPath("$.stats.matchesPlayed").value(3))
				.andExpect(jsonPath("$.stats.wins").value(1))
				.andExpect(jsonPath("$.stats.losses").value(1))
				.andExpect(jsonPath("$.stats.draws").value(1))
				.andExpect(jsonPath("$.stats.winRate").value(33.3))
				.andExpect(jsonPath("$.email").doesNotExist())
				.andExpect(jsonPath("$.shardBalance").doesNotExist())
				.andExpect(jsonPath("$.role").doesNotExist())
				.andExpect(jsonPath("$.timezone").doesNotExist())
				.andExpect(jsonPath("$.inviteCode").doesNotExist());
		mvc.perform(get("/api/v1/users/nobody_" + player.username)).andExpect(status().isNotFound());
	}

	// ------------------------------------------------------------------ helpers

	private record Session(String username, String accessToken, String refreshToken) {
	}

	private Session newPlayer() throws Exception {
		return registerAndLogin("t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
	}

	private Session registerAndLogin(String username) throws Exception {
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"username":"%s","email":"%s@example.com","password":"%s","timezone":"Europe/Rome"}
				""".formatted(username, username, PASSWORD))).andExpect(status().isCreated());
		String body = login(username, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return new Session(username, JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
	}

	private ResultActions login(String identifier, String password) throws Exception {
		return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"identifier\":\"" + identifier + "\",\"password\":\"" + password + "\"}"));
	}

	private ResultActions authed(MockHttpServletRequestBuilder request, Session s) throws Exception {
		return mvc.perform(request.header("Authorization", "Bearer " + s.accessToken));
	}

	private ResultActions patchMe(Session s, String json) throws Exception {
		return authed(patch("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON).content(json), s);
	}

	private ResultActions changePassword(Session s, String current, String next) throws Exception {
		return authed(put("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"), s);
	}

	private ResultActions deleteMe(Session s, String password) throws Exception {
		return authed(delete("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"" + password + "\"}"), s);
	}

	private void insertPlayedBattle(Session challenger, Session opponent, String outcome) {
		jdbc.update("""
				insert into battle (mode, status, challenger_id, opponent_id, outcome, decks_snapshot, played_at)
				values ('QUICK', 'PLAYED', (select id from player where username = ?),
						(select id from player where username = ?), ?, '{}'::jsonb, now())
				""", challenger.username, opponent.username, outcome);
	}
}
