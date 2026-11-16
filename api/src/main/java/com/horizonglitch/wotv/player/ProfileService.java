package com.horizonglitch.wotv.player;

import com.horizonglitch.wotv.auth.TokenService;
import com.horizonglitch.wotv.auth.TokenType;
import com.horizonglitch.wotv.battle.BattleStatsService;
import com.horizonglitch.wotv.common.ApiError;
import com.horizonglitch.wotv.common.ApiException;
import com.horizonglitch.wotv.common.PasswordRules;
import com.horizonglitch.wotv.common.TimezoneRules;
import com.horizonglitch.wotv.config.AppProperties;
import com.horizonglitch.wotv.season.Orbit;
import com.horizonglitch.wotv.trade.TradeService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profile routes of api-contract-draft/01-auth-profile.md, section 2. */
@Service
public class ProfileService {

	private final PlayerRepository players;
	private final PlayerSettingsRepository settings;
	private final AvatarRepository avatars;
	private final BattleStatsService battleStats;
	private final TokenService tokenService;
	private final TradeService tradeService;
	private final PasswordEncoder passwordEncoder;
	private final AppProperties properties;
	private final Clock clock;

	public ProfileService(PlayerRepository players, PlayerSettingsRepository settings, AvatarRepository avatars,
			BattleStatsService battleStats, TokenService tokenService, TradeService tradeService,
			PasswordEncoder passwordEncoder,
			AppProperties properties, Clock clock) {
		this.players = players;
		this.settings = settings;
		this.avatars = avatars;
		this.battleStats = battleStats;
		this.tokenService = tokenService;
		this.tradeService = tradeService;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ProfileResponses.Me me(long playerId) {
		return toMe(current(playerId));
	}

	/**
	 * Changes avatar and time zone. The time zone can change once every {@code timezoneChangeDays}: the
	 * player's row is locked, so two simultaneous requests cannot both change it.
	 */
	@Transactional
	public ProfileResponses.Me update(long playerId, ProfileRequests.UpdateProfile request) {
		Player player = players.lockById(playerId).orElseThrow(ProfileService::tokenInvalid);

		Avatar avatar = null;
		List<ApiError.FieldErrorItem> errors = new ArrayList<>();
		if (request.avatar() != null) {
			avatar = avatars.findByNameAndActiveTrue(request.avatar()).orElse(null);
			if (avatar == null) {
				errors.add(new ApiError.FieldErrorItem("avatar", "AVATAR_INVALID"));
			}
		}
		boolean changeTimezone = request.timezone() != null && !request.timezone().equals(player.getTimezone());
		if (changeTimezone && !TimezoneRules.isIana(request.timezone())) {
			errors.add(new ApiError.FieldErrorItem("timezone", "TIMEZONE_INVALID"));
		}
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}

		if (changeTimezone) {
			Instant now = clock.instant();
			Instant last = player.getTimezoneChangedAt();
			Duration interval = Duration.ofDays(properties.economy().timezoneChangeDays());
			if (last != null && last.plus(interval).isAfter(now)) {
				throw new ApiException(HttpStatus.CONFLICT, "TIMEZONE_CHANGE_TOO_SOON",
						"The time zone can be changed again after " + last.plus(interval) + ".");
			}
			player.setTimezone(request.timezone());
			player.setTimezoneChangedAt(now);
		}
		if (avatar != null) {
			player.setAvatar(avatar);
		}
		return toMe(player);
	}

	@Transactional(readOnly = true)
	public ProfileResponses.Settings settings(long playerId) {
		return ProfileResponses.Settings.of(settingsOf(playerId));
	}

	@Transactional
	public ProfileResponses.Settings updateSettings(long playerId, ProfileRequests.UpdateSettings request) {
		PlayerSettings current = settingsOf(playerId);
		if (request.reduceMotion() != null) {
			current.setReduceMotion(request.reduceMotion());
		}
		if (request.reduceTransparency() != null) {
			current.setReduceTransparency(request.reduceTransparency());
		}
		if (request.showTextLabels() != null) {
			current.setShowTextLabels(request.showTextLabels());
		}
		return ProfileResponses.Settings.of(current);
	}

	/** Sets the new password and invalidates every refresh token: the other devices must log in again. */
	@Transactional
	public void changePassword(long playerId, ProfileRequests.ChangePassword request) {
		if (PasswordRules.tooLong(request.newPassword())) {
			throw ApiException.validation(List.of(new ApiError.FieldErrorItem("newPassword", "PASSWORD_TOO_LONG")));
		}
		Player player = current(playerId);
		checkPassword(player, request.currentPassword());
		player.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		tokenService.revokeAll(player, TokenType.REFRESH);
	}

	/**
	 * Soft delete: the row stays so battles and trades keep their history, but username and email are
	 * anonymised, every token is invalidated, pending trades are cancelled and the player is no longer attackable.
	 */
	@Transactional
	public void delete(long playerId, ProfileRequests.DeleteAccount request) {
		Player player = current(playerId);
		checkPassword(player, request.password());

		player.setDeletedAt(clock.instant());
		player.setUsername("deleted_" + player.getId());
		player.setEmail("deleted_" + player.getId() + "@deleted.invalid");
		player.setDefenseDeck(null);
		tradeService.cancelAllPending(player);
		for (TokenType type : TokenType.values()) {
			tokenService.revokeAll(player, type);
		}
	}

	@Transactional(readOnly = true)
	public ProfileResponses.Public publicProfile(String username) {
		Player player = players.findActiveByUsername(username)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAYER_NOT_FOUND",
						"The player does not exist."));
		return new ProfileResponses.Public(player.getId(), player.getUsername(), avatarKey(player),
				player.getTrophies(), Orbit.fromTrophies(player.getTrophies()), battleStats.statsOf(player.getId()),
				player.getCreatedAt());
	}

	private ProfileResponses.Me toMe(Player player) {
		return new ProfileResponses.Me(
				player.getId(),
				player.getUsername(),
				player.getEmail(),
				player.getEmailVerifiedAt() != null,
				player.getRole(),
				avatarKey(player),
				player.getShardBalance(),
				player.getTrophies(),
				Orbit.fromTrophies(player.getTrophies()),
				player.getDefenseDeck() != null ? player.getDefenseDeck().getId() : null,
				activeShield(player),
				player.getTimezone(),
				player.getInviteCode(),
				player.getInvitedBy() != null ? player.getInvitedBy().getUsername() : null,
				battleStats.statsOf(player.getId()),
				player.getCreatedAt());
	}

	/** {@code null} when the player is not protected, including a shield that has already ended. */
	private Instant activeShield(Player player) {
		Instant until = player.getProtectedUntil();
		return until != null && until.isAfter(clock.instant()) ? until : null;
	}

	private static String avatarKey(Player player) {
		return player.getAvatar() != null ? player.getAvatar().getName() : null;
	}

	/** The authenticated player; a deleted account behaves like an invalid token. */
	private Player current(long playerId) {
		return players.findByIdAndDeletedAtIsNull(playerId).orElseThrow(ProfileService::tokenInvalid);
	}

	private PlayerSettings settingsOf(long playerId) {
		current(playerId);
		return settings.findById(playerId).orElseThrow(ProfileService::tokenInvalid);
	}

	private void checkPassword(Player player, String password) {
		if (!passwordEncoder.matches(password, player.getPasswordHash())) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "The password is wrong.");
		}
	}

	private static ApiException tokenInvalid() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "The account no longer exists.");
	}
}
