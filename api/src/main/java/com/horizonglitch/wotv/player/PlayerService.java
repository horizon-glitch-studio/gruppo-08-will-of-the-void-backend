package com.horizonglitch.wotv.player;

import com.horizonglitch.wotv.common.ApiException;
import java.security.SecureRandom;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates and finds Summoners. Deleted accounts are never returned. */
@Service
public class PlayerService {

	private static final String INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int INVITE_LENGTH = 6;

	private final PlayerRepository players;
	private final PlayerSettingsRepository settings;
	private final SecureRandom random = new SecureRandom();

	public PlayerService(PlayerRepository players, PlayerSettingsRepository settings) {
		this.players = players;
		this.settings = settings;
	}

	/**
	 * Saves the player with a new invite code and the default settings. Username and email are unique ignoring
	 * case: the database indexes catch two registrations at the same instant.
	 */
	@Transactional
	public Player create(String username, String email, String passwordHash, String timezone, Player invitedBy) {
		if (players.existsByUsernameIgnoringCase(username)) {
			throw usernameUsed();
		}
		if (players.existsByEmailIgnoringCase(email)) {
			throw emailUsed();
		}

		Player player = new Player();
		player.setUsername(username);
		player.setEmail(email);
		player.setPasswordHash(passwordHash);
		player.setTimezone(timezone);
		player.setInvitedBy(invitedBy);
		player.setInviteCode(newInviteCode());
		try {
			players.saveAndFlush(player);
		} catch (DataIntegrityViolationException e) {
			String message = String.valueOf(e.getMostSpecificCause().getMessage());
			if (message.contains("uq_player_username_lower")) {
				throw usernameUsed();
			}
			if (message.contains("uq_player_email_lower")) {
				throw emailUsed();
			}
			throw e;
		}

		PlayerSettings defaults = new PlayerSettings();
		defaults.setPlayer(player);
		settings.save(defaults);
		return player;
	}

	public Optional<Player> findActiveByIdentifier(String identifier) {
		return players.findActiveByUsername(identifier).or(() -> players.findActiveByEmail(identifier));
	}

	public Optional<Player> findActiveByUsername(String username) {
		return players.findActiveByUsername(username);
	}

	public Optional<Player> findActiveByEmail(String email) {
		return players.findActiveByEmail(email);
	}

	public Optional<Player> findActiveByInviteCode(String inviteCode) {
		return players.findByInviteCodeAndDeletedAtIsNull(inviteCode);
	}

	public Player getActive(long id) {
		return players.findByIdAndDeletedAtIsNull(id).orElseThrow(PlayerService::notFound);
	}

	/** Locks the player's row: used where two simultaneous requests must not both apply (daily bonus). */
	public Player lockById(long id) {
		return players.lockById(id).orElseThrow(PlayerService::notFound);
	}

	private String newInviteCode() {
		for (int attempt = 0; attempt < 10; attempt++) {
			StringBuilder code = new StringBuilder("VOID-");
			for (int i = 0; i < INVITE_LENGTH; i++) {
				code.append(INVITE_ALPHABET.charAt(random.nextInt(INVITE_ALPHABET.length())));
			}
			if (!players.existsByInviteCode(code.toString())) {
				return code.toString();
			}
		}
		throw new IllegalStateException("Could not generate a unique invite code");
	}

	private static ApiException usernameUsed() {
		return new ApiException(HttpStatus.CONFLICT, "USERNAME_ALREADY_USED", "This username is already in use.");
	}

	private static ApiException emailUsed() {
		return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED", "This email is already registered.");
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "PLAYER_NOT_FOUND", "The player does not exist.");
	}
}
