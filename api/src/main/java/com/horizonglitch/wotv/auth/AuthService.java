package com.horizonglitch.wotv.auth;

import com.horizonglitch.wotv.card.StarterDeckCard;
import com.horizonglitch.wotv.card.StarterDeckService;
import com.horizonglitch.wotv.collection.CollectionService;
import com.horizonglitch.wotv.common.ApiError;
import com.horizonglitch.wotv.common.ApiException;
import com.horizonglitch.wotv.common.mail.EmailSender;
import com.horizonglitch.wotv.config.AppProperties;
import com.horizonglitch.wotv.deck.DeckService;
import com.horizonglitch.wotv.pack.PackGuaranteeService;
import com.horizonglitch.wotv.player.Player;
import com.horizonglitch.wotv.player.PlayerService;
import com.horizonglitch.wotv.shard.LoginBonuses;
import com.horizonglitch.wotv.shard.ShardService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, login and the tokens of api-contract-draft/01-auth-profile.md, section 1. */
@Service
public class AuthService {

	private static final Set<String> RESERVED_USERNAMES = Set.of(
			"me", "admin", "administrator", "root", "system", "support", "moderator", "null", "undefined");

	/** BCrypt hashes at most 72 bytes: longer passwords are refused instead of being truncated. */
	private static final int PASSWORD_MAX_BYTES = 72;

	private final PlayerService playerService;
	private final ShardService shardService;
	private final StarterDeckService starterDeckService;
	private final CollectionService collectionService;
	private final DeckService deckService;
	private final PackGuaranteeService packGuaranteeService;
	private final TokenService tokenService;
	private final PasswordEncoder passwordEncoder;
	private final EmailSender emailSender;
	private final AppProperties properties;
	private final Clock clock;
	private final String dummyHash;

	public AuthService(PlayerService playerService, ShardService shardService, StarterDeckService starterDeckService,
			CollectionService collectionService, DeckService deckService, PackGuaranteeService packGuaranteeService,
			TokenService tokenService, PasswordEncoder passwordEncoder, EmailSender emailSender,
			AppProperties properties, Clock clock) {
		this.playerService = playerService;
		this.shardService = shardService;
		this.starterDeckService = starterDeckService;
		this.collectionService = collectionService;
		this.deckService = deckService;
		this.packGuaranteeService = packGuaranteeService;
		this.tokenService = tokenService;
		this.passwordEncoder = passwordEncoder;
		this.emailSender = emailSender;
		this.properties = properties;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode("not-a-real-password");
	}

	/**
	 * Creates the account with its settings and guarantee counters, gives the registration bonus and the starter
	 * deck, and sends the verification email, all in one transaction.
	 */
	@Transactional
	public AuthResponses.Registered register(AuthRequests.Register request) {
		List<ApiError.FieldErrorItem> errors = new ArrayList<>();
		if (RESERVED_USERNAMES.contains(request.username().toLowerCase(Locale.ROOT))) {
			errors.add(new ApiError.FieldErrorItem("username", "USERNAME_RESERVED"));
		}
		if (tooLong(request.password())) {
			errors.add(new ApiError.FieldErrorItem("password", "PASSWORD_TOO_LONG"));
		}
		if (!isIanaTimezone(request.timezone())) {
			errors.add(new ApiError.FieldErrorItem("timezone", "TIMEZONE_INVALID"));
		}
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}

		Player inviter = null;
		if (request.inviteCode() != null && !request.inviteCode().isBlank()) {
			inviter = playerService.findActiveByInviteCode(request.inviteCode().trim())
					.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVITE_CODE_INVALID",
							"This invite code does not exist."));
		}

		Player player = playerService.create(request.username(), request.email().trim(),
				passwordEncoder.encode(request.password()), request.timezone(), inviter);
		packGuaranteeService.createFor(player);
		shardService.grantRegistrationBonus(player);

		List<StarterDeckCard> starterDeck = starterDeckService.getStarterDeck();
		if (!starterDeck.isEmpty()) {
			collectionService.addStarterCards(player, starterDeck);
			deckService.createStarterDeck(player, starterDeck);
		}

		sendVerificationEmail(player);
		return new AuthResponses.Registered(player.getId(), player.getUsername(), player.getEmail(), false);
	}

	/** Same answer for an unknown identifier and a wrong password, and a similar response time. */
	@Transactional
	public AuthResponses.Login login(AuthRequests.Login request) {
		Player found = playerService.findActiveByIdentifier(request.identifier().trim()).orElse(null);
		String hash = found != null ? found.getPasswordHash() : dummyHash;
		boolean matches = passwordEncoder.matches(request.password(), hash);
		if (found == null || !matches) {
			throw invalidCredentials();
		}

		Player player = playerService.lockById(found.getId());
		LoginBonuses bonuses = shardService.grantLoginBonuses(player);
		TokenService.IssuedTokens tokens = tokenService.issue(player);

		return new AuthResponses.Login("Bearer", tokens.accessToken(), tokens.accessTokenExpiresIn(),
				tokens.refreshToken(), tokens.refreshTokenExpiresIn(),
				new AuthResponses.PlayerSummary(player.getId(), player.getUsername(), player.getRole(),
						player.getEmailVerifiedAt() != null),
				AuthResponses.Bonus.of(bonuses.daily()), AuthResponses.Bonus.of(bonuses.comeback()));
	}

	/** Rotation: the refresh token is used once and replaced by a new one. */
	@Transactional
	public AuthResponses.Tokens refresh(String refreshToken) {
		PlayerToken used = tokenService.consume(refreshToken, TokenType.REFRESH)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID",
						"The refresh token is invalid, expired or already used."));
		TokenService.IssuedTokens tokens = tokenService.issue(used.getPlayer());
		return new AuthResponses.Tokens("Bearer", tokens.accessToken(), tokens.accessTokenExpiresIn(),
				tokens.refreshToken(), tokens.refreshTokenExpiresIn());
	}

	/** Invalidates the refresh token if it belongs to the caller; the answer is the same otherwise. */
	@Transactional
	public void logout(long playerId, String refreshToken) {
		tokenService.consume(refreshToken, TokenType.REFRESH)
				.filter(t -> !t.getPlayer().getId().equals(playerId))
				.ifPresent(t -> {
					throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID",
							"The refresh token does not belong to this player.");
				});
	}

	@Transactional
	public void verifyEmail(String token) {
		PlayerToken used = tokenService.consume(token, TokenType.EMAIL_VERIFICATION)
				.orElseThrow(AuthService::actionTokenInvalid);
		Player player = used.getPlayer();
		if (player.getEmailVerifiedAt() == null) {
			player.setEmailVerifiedAt(clock.instant());
		}
	}

	/** Answers 204 even when the email is already verified. */
	@Transactional
	public void resendVerification(long playerId) {
		Player player = playerService.getActive(playerId);
		if (player.getEmailVerifiedAt() == null) {
			tokenService.revokeAll(player, TokenType.EMAIL_VERIFICATION);
			sendVerificationEmail(player);
		}
	}

	/** Answers 202 whether or not the email exists, so registered emails are not revealed. */
	@Transactional
	public void forgotPassword(String email) {
		playerService.findActiveByEmail(email.trim()).ifPresent(player -> {
			tokenService.revokeAll(player, TokenType.PASSWORD_RESET);
			String token = tokenService.create(player, TokenType.PASSWORD_RESET, properties.tokens().passwordReset());
			emailSender.send(player.getEmail(), "Reset your Will of the Void password",
					"Open this link to choose a new password: " + properties.frontendUrl()
							+ "/reset-password?token=" + token);
		});
	}

	/** Sets the new password and logs the player out of every device. */
	@Transactional
	public void resetPassword(String token, String newPassword) {
		if (tooLong(newPassword)) {
			throw ApiException.validation(List.of(new ApiError.FieldErrorItem("newPassword", "PASSWORD_TOO_LONG")));
		}
		PlayerToken used = tokenService.consume(token, TokenType.PASSWORD_RESET)
				.orElseThrow(AuthService::actionTokenInvalid);
		Player player = used.getPlayer();
		player.setPasswordHash(passwordEncoder.encode(newPassword));
		tokenService.revokeAll(player, TokenType.REFRESH);
	}

	private void sendVerificationEmail(Player player) {
		String token = tokenService.create(player, TokenType.EMAIL_VERIFICATION,
				properties.tokens().emailVerification());
		emailSender.send(player.getEmail(), "Verify your Will of the Void email",
				"Open this link to verify your email: " + properties.frontendUrl() + "/verify-email?token=" + token);
	}

	private static boolean tooLong(String password) {
		return password != null && password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES;
	}

	/** Only region ids such as Europe/Rome: offsets like +01:00 do not follow daylight saving time. */
	static boolean isIanaTimezone(String timezone) {
		return timezone != null && timezone.contains("/") && ZoneId.getAvailableZoneIds().contains(timezone);
	}

	private static ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Wrong username, email or password.");
	}

	private static ApiException actionTokenInvalid() {
		return new ApiException(HttpStatus.BAD_REQUEST, "ACTION_TOKEN_INVALID",
				"The token is incorrect, expired or already used.");
	}
}
