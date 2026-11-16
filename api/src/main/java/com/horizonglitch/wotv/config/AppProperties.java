package com.horizonglitch.wotv.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code app.*} in application.properties. */
@ConfigurationProperties("app")
public record AppProperties(String frontendUrl, Cors cors, Jwt jwt, Tokens tokens, Economy economy, Decks decks, Packs packs, Trades trades, Battles battles, Matchmaking matchmaking) {

	public record Cors(List<String> allowedOrigins) {
	}

	/** {@code secret} is a Base64 key of at least 32 bytes, used to sign access tokens with HS256. */
	public record Jwt(String secret, long accessTokenExpirationMs, long refreshTokenExpirationMs) {
	}

	/** Lifetime of the email verification and password reset links. */
	public record Tokens(Duration emailVerification, Duration passwordReset) {
	}

	/**
	 * Guarantees of rules/packs.md (PACK-08 to PACK-10) and the names of the rarities they refer to: a guarantee is
	 * due on the {@code championPacks}-th pack in a row without a Champion card or rarer, and so on.
	 */
	public record Packs(int championPacks, int electPacks, int wandererPacks, String championRarity,
			String electRarity, String wandererRarity) {
	}

	/**
	 * Trades (rules/trades.md): a proposal lasts {@code expiry} (TRADE_EXPIRY_HOURS) and nobody can be left with
	 * fewer than {@code minCollectionSize} permanent cards (MIN_COLLECTION_SIZE).
	 */
	public record Trades(java.time.Duration expiry, int minCollectionSize) {
	}

	/**
	 * Match parameters of rules/parameters.md, passed to the engine: the win thresholds by mode (MATCH-12), the
	 * end bonus per card (END_BONUS_PER_CARD) and the Elo factor (ELO_K). A challenge lasts {@code challengeExpiry}
	 * (CHALLENGE_EXPIRY_HOURS) and a replay can be watched for {@code replayRetention} (REPLAY_RETENTION_DAYS).
	 */
	public record Battles(int winThresholdFriend, int winThresholdRanked, int winThresholdQuick, int endBonusPerCard,
			int eloK, Duration challengeExpiry, Duration replayRetention) {
	}

	/**
	 * Opponent lists, attack limits and shield of rules/game-modes.md (MODE-09 to MODE-22).
	 * {@code trophyRanges} holds the search range of each Orbit, from Orbit 1 to Orbit 4 (MODE-12).
	 */
	public record Matchmaking(int listSize, Duration refreshCooldown, Duration quickActive, List<Integer> trophyRanges,
			int rankedAttacksPerOpponent, int quickAttacksPerOpponent, Duration attackWindow,
			int shieldTriggerAttacks, Duration shieldWindow, Duration shieldDuration) {
	}

	/** Deck limits of rules/decks.md: DECK_SIZE, MAX_PER_FACTION and MAX_SAVED_DECKS. */
	public record Decks(int size, int maxPerElementalFaction, int maxSaved) {
	}

	/** Every bonus is a multiple of the daily bonus (rules/economy.md). */
	public record Economy(
			int dailyBonus,
			int registrationBonusMultiplier,
			int comebackBonusMultiplier,
			int comebackAbsenceDays,
			int comebackIntervalDays,
			int timezoneChangeDays) {
	}
}
