package com.horizonglitch.wotv.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code app.*} in application.properties. */
@ConfigurationProperties("app")
public record AppProperties(String frontendUrl, Cors cors, Jwt jwt, Tokens tokens, Economy economy, Decks decks, Packs packs) {

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
