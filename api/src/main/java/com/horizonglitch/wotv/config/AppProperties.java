package com.horizonglitch.wotv.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code app.*} in application.properties. */
@ConfigurationProperties("app")
public record AppProperties(String frontendUrl, Cors cors, Jwt jwt, Tokens tokens, Economy economy) {

	public record Cors(List<String> allowedOrigins) {
	}

	/** {@code secret} is a Base64 key of at least 32 bytes, used to sign access tokens with HS256. */
	public record Jwt(String secret, long accessTokenExpirationMs, long refreshTokenExpirationMs) {
	}

	/** Lifetime of the email verification and password reset links. */
	public record Tokens(Duration emailVerification, Duration passwordReset) {
	}

	/** Every bonus is a multiple of the daily bonus (rules/economy.md). */
	public record Economy(
			int dailyBonus,
			int registrationBonusMultiplier,
			int comebackBonusMultiplier,
			int comebackAbsenceDays,
			int comebackIntervalDays) {
	}
}
