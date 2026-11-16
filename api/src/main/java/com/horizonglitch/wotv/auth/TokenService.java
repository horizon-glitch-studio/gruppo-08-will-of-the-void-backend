package com.horizonglitch.wotv.auth;

import com.horizonglitch.wotv.config.AppProperties;
import com.horizonglitch.wotv.security.JwtConfig;
import com.horizonglitch.wotv.player.Player;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues access tokens (JWT) and the random tokens stored in {@code player_token}: refresh, email verification
 * and password reset. Only the SHA-256 hash of a random token is stored.
 */
@Service
public class TokenService {

	private static final String ISSUER = "willofthevoid";

	private final JwtEncoder jwtEncoder;
	private final PlayerTokenRepository tokens;
	private final AppProperties properties;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public TokenService(JwtEncoder jwtEncoder, PlayerTokenRepository tokens, AppProperties properties, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.tokens = tokens;
		this.properties = properties;
		this.clock = clock;
	}

	/** A new access token and a new refresh token for the player. */
	public IssuedTokens issue(Player player) {
		Instant now = clock.instant();
		Duration accessTtl = Duration.ofMillis(properties.jwt().accessTokenExpirationMs());
		Duration refreshTtl = Duration.ofMillis(properties.jwt().refreshTokenExpirationMs());

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.subject(player.getId().toString())
				.issuedAt(now)
				.expiresAt(now.plus(accessTtl))
				.claim(JwtConfig.ROLE_CLAIM, player.getRole().name())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		String refreshToken = create(player, TokenType.REFRESH, refreshTtl);

		return new IssuedTokens(accessToken, accessTtl.toSeconds(), refreshToken, refreshTtl.toSeconds());
	}

	/** Stores a new random token of the given type and returns its raw value, which is never stored. */
	public String create(Player player, TokenType type, Duration ttl) {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

		PlayerToken token = new PlayerToken();
		token.setPlayer(player);
		token.setType(type);
		token.setTokenHash(hash(raw));
		token.setExpiresAt(clock.instant().plus(ttl));
		tokens.save(token);
		return raw;
	}

	/**
	 * Uses a token once: it must exist, have the given type, be unused and not expired, and belong to an active
	 * player. The token is marked as used with a conditional UPDATE, so two simultaneous requests cannot both
	 * use it.
	 */
	public Optional<PlayerToken> consume(String raw, TokenType type) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		Instant now = clock.instant();
		return tokens.findByTokenHash(hash(raw))
				.filter(t -> t.getType() == type)
				.filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now))
				.filter(t -> t.getPlayer().getDeletedAt() == null)
				.filter(t -> tokens.markUsed(t.getId(), now) == 1);
	}

	/** Marks every unused token of that type of the player as used. */
	public void revokeAll(Player player, TokenType type) {
		tokens.markAllUsed(player.getId(), type, clock.instant());
	}

	static String hash(String raw) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	/** Lifetimes are in seconds, as in the API contract. */
	public record IssuedTokens(String accessToken, long accessTokenExpiresIn, String refreshToken,
			long refreshTokenExpiresIn) {
	}
}
