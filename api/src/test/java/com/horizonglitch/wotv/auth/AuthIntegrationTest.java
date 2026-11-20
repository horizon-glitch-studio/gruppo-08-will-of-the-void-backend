package com.horizonglitch.wotv.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.horizonglitch.wotv.common.mail.EmailSender;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Auth routes against the real schema. Every test registers its own players, so tests do not interfere. */
@SpringBootTest
class AuthIntegrationTest {

	private static final String PASSWORD = "Password123!";

	@TestConfiguration
	static class MailConfig {
		@Bean
		@Primary
		RecordingEmailSender recordingEmailSender() {
			return new RecordingEmailSender();
		}
	}

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EmailSender emailSender;

	@Autowired
	private com.horizonglitch.wotv.config.AppProperties properties;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void registersAndRefusesTheSameUsernameOrEmailIgnoringCase() throws Exception {
		String name = uniqueName();
		register(name, name + "@example.com")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value(name))
				.andExpect(jsonPath("$.emailVerified").value(false));

		register(name.toUpperCase(), "other_" + name + "@example.com")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("USERNAME_ALREADY_USED"));
		register("o" + name, name.toUpperCase() + "@EXAMPLE.COM")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));

		Long id = jdbc.queryForObject("select id from player where username = ?", Long.class, name);
		assertThat(jdbc.queryForObject("select count(*) from player_settings where player_id = ?", Integer.class, id)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from pack_guarantee where player_id = ?", Integer.class, id)).isEqualTo(1);
		int registrationBonus = properties.economy().dailyBonus() * properties.economy().registrationBonusMultiplier();
		assertThat(balance(name)).isEqualTo(registrationBonus);
	}

	@Test
	void reportsEveryInvalidFieldWithItsCode() throws Exception {
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"username":"ab","email":"not-an-email","password":"short","timezone":"Mars/Base"}
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field=='username')].code").value("USERNAME_TOO_SHORT"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field=='email')].code").value("EMAIL_INVALID"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field=='password')].code").value("PASSWORD_TOO_SHORT"));

		String name = uniqueName();
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"username":"admin","email":"%s@example.com","password":"%s","timezone":"Mars/Base"}
				""".formatted(name, PASSWORD)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field=='username')].code").value("USERNAME_RESERVED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field=='timezone')].code").value("TIMEZONE_INVALID"));
	}

	@Test
	void refusesAnUnknownInviteCodeAndLinksAKnownOne() throws Exception {
		String inviter = uniqueName();
		register(inviter, inviter + "@example.com").andExpect(status().isCreated());
		String code = jdbc.queryForObject("select invite_code from player where username = ?", String.class, inviter);

		String invited = uniqueName();
		registerWithInvite(invited, "NOPE-000000").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVITE_CODE_INVALID"));
		registerWithInvite(invited, code).andExpect(status().isCreated());
		assertThat(jdbc.queryForObject("select i.username from player p join player i on i.id = p.invited_by_id "
				+ "where p.username = ?", String.class, invited)).isEqualTo(inviter);
	}

	@Test
	void grantsInviteBonusWhenVerifiedInviteeHasPlayedTenDifferentOpponents() throws Exception {
		String inviter = uniqueName();
		register(inviter, inviter + "@example.com").andExpect(status().isCreated());
		String inviteCode = jdbc.queryForObject("select invite_code from player where username = ?", String.class, inviter);

		String invited = uniqueName();
		String invitedEmail = invited + "@example.com";
		registerWithInvite(invited, inviteCode).andExpect(status().isCreated());
		Long invitedId = jdbc.queryForObject("select id from player where username = ?", Long.class, invited);
		Long inviterId = jdbc.queryForObject("select id from player where username = ?", Long.class, inviter);
		for (int i = 0; i < 10; i++) {
			String opponent = uniqueName();
			String opponentCode = "VOID-" + opponent.substring(2, 10);
			Long opponentId = jdbc.queryForObject("insert into player (username, email, password_hash, invite_code) "
					+ "values (?, ?, 'unused', ?) returning id", Long.class, opponent, opponent + "@example.com", opponentCode);
			jdbc.update("insert into battle (mode, status, challenger_id, opponent_id, outcome, decks_snapshot, played_at) "
					+ "values ('FRIEND', 'PLAYED', ?, ?, 'DRAW', '{}'::jsonb, now())", invitedId, opponentId);
		}

		int balanceBefore = balance(inviter);
		String verificationToken = recorder().lastToken(invitedEmail, "Verify");
		actionToken("/api/v1/auth/verify-email", verificationToken).andExpect(status().isNoContent());

		int inviteBonus = properties.economy().dailyBonus() * properties.economy().inviteBonusMultiplier();
		assertThat(balance(inviter)).isEqualTo(balanceBefore + inviteBonus);
		assertThat(jdbc.queryForObject("select count(*) from shard_transaction where player_id = ? "
				+ "and reason = 'INVITE_BONUS' and invited_player_id = ?", Integer.class, inviterId, invitedId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from notification where player_id = ? and type = 'INVITE_BONUS'",
				Integer.class, inviterId)).isEqualTo(1);
	}

	@Test
	void loginGivesTheDailyBonusOncePerDay() throws Exception {
		String name = uniqueName();
		register(name, name + "@example.com").andExpect(status().isCreated());
		int afterRegistration = balance(name);
		int daily = properties.economy().dailyBonus();

		login(name).andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.player.username").value(name))
				.andExpect(jsonPath("$.player.role").value("USER"))
				.andExpect(jsonPath("$.dailyBonus.granted").value(true))
				.andExpect(jsonPath("$.dailyBonus.amount").value(daily))
				.andExpect(jsonPath("$.comebackBonus.granted").value(false));
		login(name.toUpperCase()).andExpect(status().isOk())
				.andExpect(jsonPath("$.dailyBonus.granted").value(false));
		loginWith(name + "@example.com", PASSWORD).andExpect(status().isOk());

		assertThat(balance(name)).isEqualTo(afterRegistration + daily);
	}

	@Test
	void comebackBonusAfterSevenDaysAway() throws Exception {
		String name = uniqueName();
		register(name, name + "@example.com").andExpect(status().isCreated());
		login(name).andExpect(status().isOk());
		jdbc.update("update player set last_daily_bonus_at = now() - interval '8 days' where username = ?", name);

		int comeback = properties.economy().dailyBonus() * properties.economy().comebackBonusMultiplier();
		login(name).andExpect(status().isOk())
				.andExpect(jsonPath("$.dailyBonus.granted").value(true))
				.andExpect(jsonPath("$.comebackBonus.granted").value(true))
				.andExpect(jsonPath("$.comebackBonus.amount").value(comeback));
		Long playerId = jdbc.queryForObject("select id from player where username = ?", Long.class, name);
		assertThat(jdbc.queryForObject("select count(*) from shard_transaction where player_id = ? "
				+ "and reason = 'DAILY_BONUS'", Integer.class, playerId)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from shard_transaction where player_id = ? "
				+ "and reason = 'COMEBACK_BONUS'", Integer.class, playerId)).isEqualTo(1);
		assertThat(jdbc.queryForList("select amount from shard_transaction where player_id = ? "
				+ "and reason = 'DAILY_BONUS' order by id", Integer.class, playerId))
				.containsExactly(properties.economy().dailyBonus(), properties.economy().dailyBonus());
		assertThat(jdbc.queryForObject("select amount from shard_transaction where player_id = ? "
				+ "and reason = 'COMEBACK_BONUS'", Integer.class, playerId)).isEqualTo(comeback);

		jdbc.update("update player set last_daily_bonus_at = now() - interval '8 days' where username = ?", name);
		login(name).andExpect(status().isOk())
				.andExpect(jsonPath("$.comebackBonus.granted").value(false));
		assertThat(jdbc.queryForObject("select count(*) from notification n join player p on p.id = n.player_id "
				+ "where p.username = ? and n.type = 'COMEBACK_BONUS'", Integer.class, name)).isEqualTo(1);
	}

	@Test
	void wrongPasswordAndUnknownPlayerGiveTheSameError() throws Exception {
		String name = uniqueName();
		register(name, name + "@example.com").andExpect(status().isCreated());
		loginWith(name, "WrongPassword1").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Unauthorized"))
				.andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
				.andExpect(jsonPath("$.timestamp").value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
				.andExpect(jsonPath("$.fieldErrors").isEmpty());
		loginWith("nobody_" + name, PASSWORD).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void refreshRotatesTheTokenAndLogoutInvalidatesIt() throws Exception {
		String name = uniqueName();
		register(name, name + "@example.com").andExpect(status().isCreated());
		String body = login(name).andReturn().getResponse().getContentAsString();
		String refresh = JsonPath.read(body, "$.refreshToken");

		String refreshed = refresh(refresh).andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		refresh(refresh).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

		String access = JsonPath.read(refreshed, "$.accessToken");
		String newRefresh = JsonPath.read(refreshed, "$.refreshToken");
		mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + access)
				.contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + newRefresh + "\"}"))
				.andExpect(status().isNoContent());
		refresh(newRefresh).andExpect(status().isUnauthorized());
	}

	@Test
	void protectedRoutesNeedAValidAccessToken() throws Exception {
		mvc.perform(post("/api/v1/auth/resend-verification"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
		mvc.perform(post("/api/v1/auth/resend-verification").header("Authorization", "Bearer not.a.jwt"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
		mvc.perform(get("/api/v1/backoffice/anything").header("Authorization", "Bearer " + accessToken()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void verifiesTheEmailOnceWithTheTokenSentByEmail() throws Exception {
		String name = uniqueName();
		String email = name + "@example.com";
		register(name, email).andExpect(status().isCreated());
		String token = recorder().lastToken(email, "Verify");

		actionToken("/api/v1/auth/verify-email", token).andExpect(status().isNoContent());
		actionToken("/api/v1/auth/verify-email", token).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ACTION_TOKEN_INVALID"));
		login(name).andExpect(jsonPath("$.player.emailVerified").value(true));
	}

	@Test
	void resetsThePasswordAndLogsOutEveryDevice() throws Exception {
		String name = uniqueName();
		String email = name + "@example.com";
		register(name, email).andExpect(status().isCreated());
		String oldRefresh = JsonPath.read(login(name).andReturn().getResponse().getContentAsString(), "$.refreshToken");

		mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}")).andExpect(status().isAccepted());
		mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nobody_" + email + "\"}")).andExpect(status().isAccepted());

		String token = recorder().lastToken(email, "Reset");
		mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + token + "\",\"newPassword\":\"NewPassword456!\"}"))
				.andExpect(status().isNoContent());

		loginWith(name, PASSWORD).andExpect(status().isUnauthorized());
		loginWith(name, "NewPassword456!").andExpect(status().isOk());
		refresh(oldRefresh).andExpect(status().isUnauthorized());
	}

	// ------------------------------------------------------------------ helpers

	private RecordingEmailSender recorder() {
		return (RecordingEmailSender) emailSender;
	}

	private String accessToken() throws Exception {
		String name = uniqueName();
		register(name, name + "@example.com").andExpect(status().isCreated());
		return JsonPath.read(login(name).andReturn().getResponse().getContentAsString(), "$.accessToken");
	}

	private ResultActions register(String username, String email) throws Exception {
		return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"username":"%s","email":"%s","password":"%s","timezone":"Europe/Rome"}
				""".formatted(username, email, PASSWORD)));
	}

	private ResultActions registerWithInvite(String username, String inviteCode) throws Exception {
		return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"username":"%s","email":"%s@example.com","password":"%s","timezone":"Europe/Rome","inviteCode":"%s"}
				""".formatted(username, username, PASSWORD, inviteCode)));
	}

	private ResultActions login(String identifier) throws Exception {
		return loginWith(identifier, PASSWORD);
	}

	private ResultActions loginWith(String identifier, String password) throws Exception {
		return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"identifier\":\"" + identifier + "\",\"password\":\"" + password + "\"}"));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}"));
	}

	private ResultActions actionToken(String path, String token) throws Exception {
		return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + token + "\"}"));
	}

	private int balance(String username) {
		return jdbc.queryForObject("select shard_balance from player where username = ?", Integer.class, username);
	}

	/** 3 to 20 characters, letters, digits and underscore. */
	private static String uniqueName() {
		return "t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
	}
}
