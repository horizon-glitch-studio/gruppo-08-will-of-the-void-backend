package com.horizonglitch.wotv.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import com.horizonglitch.wotv.player.Player;

/** Email verification, password reset or refresh token. Only the SHA-256 hash of the token is stored. */
@Entity
@Table(name = "player_token")
@Getter
@Setter
@NoArgsConstructor
public class PlayerToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id", nullable = false)
	private Player player;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TokenType type;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;
}
