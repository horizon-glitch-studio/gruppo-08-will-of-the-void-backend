package com.horizonglitch.wotv.player;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AccessLevel;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import com.horizonglitch.wotv.deck.Deck;

/** A Summoner's account.
 *
 * <p>{@code shardBalance} and {@code trophies} are read-only here: they change only with atomic UPDATE
 * statements, because other players' requests change them too (see docs/database/database-schema.md). */
@Entity
@Table(name = "player")
@Getter
@Setter
@NoArgsConstructor
public class Player {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Unique ignoring case ({@code uq_player_username_lower}). */
	@Column(nullable = false, length = 50)
	private String username;

	/** Unique ignoring case ({@code uq_player_email_lower}). */
	@Column(nullable = false)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private PlayerRole role = PlayerRole.USER;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	/** Empty means the default avatar, shown by the frontend. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "avatar_id")
	private Avatar avatar;

	/** Empty means no defense deck, so the Summoner cannot attack. */
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "defense_deck_id", unique = true)
	private Deck defenseDeck;

	@Setter(AccessLevel.NONE)
	@Column(name = "shard_balance", nullable = false, insertable = false, updatable = false)
	private int shardBalance;

	@Setter(AccessLevel.NONE)
	@Column(nullable = false, insertable = false, updatable = false)
	private int trophies;

	@Column(name = "last_daily_bonus_at")
	private Instant lastDailyBonusAt;

	/** IANA time zone, for example {@code Europe/Rome}. */
	@Column(nullable = false, length = 64)
	private String timezone = "Europe/Rome";

	@Column(name = "timezone_changed_at")
	private Instant timezoneChangedAt;

	/** End of the shield after 5 Ranked attacks received in 24 hours. */
	@Column(name = "protected_until")
	private Instant protectedUntil;

	@Column(name = "invite_code", nullable = false, unique = true, length = 20)
	private String inviteCode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "invited_by_id")
	private Player invitedBy;

	/** Optimistic locking of the profile data. */
	@Version
	@Setter(AccessLevel.NONE)
	@Column(nullable = false)
	private int version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Soft delete: username and email are anonymised when it is set. */
	@Column(name = "deleted_at")
	private Instant deletedAt;
}
