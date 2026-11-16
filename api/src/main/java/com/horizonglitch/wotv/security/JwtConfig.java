package com.horizonglitch.wotv.security;

import com.horizonglitch.wotv.config.AppProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Access tokens are JWTs signed with HS256: the API issues them and checks them as an OAuth2 resource server. */
@Configuration
public class JwtConfig {

	/** Claim holding the role (USER or ADMIN) of the player. */
	public static final String ROLE_CLAIM = "role";

	private final SecretKey key;

	public JwtConfig(AppProperties properties) {
		this.key = secretKey(properties.jwt().secret());
	}

	@Bean
	public JwtEncoder jwtEncoder() {
		return new NimbusJwtEncoder(new ImmutableSecret<>(key));
	}

	@Bean
	public JwtDecoder jwtDecoder() {
		return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
	}

	static SecretKey secretKey(String secret) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("app.jwt.secret is empty: set APP_JWT_SECRET (see .env.example)");
		}
		byte[] bytes = Base64.getDecoder().decode(secret.trim());
		if (bytes.length < 32) {
			throw new IllegalStateException("app.jwt.secret must be a Base64 key of at least 32 bytes");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}
}
