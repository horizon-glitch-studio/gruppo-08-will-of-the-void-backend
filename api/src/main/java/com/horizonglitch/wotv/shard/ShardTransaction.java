package com.horizonglitch.wotv.shard;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import com.horizonglitch.wotv.pack.PackOpening;
import com.horizonglitch.wotv.player.Player;
import com.horizonglitch.wotv.season.Season;

/** A Shard income or expense. Rows are never changed after they are written.
 *
 * <p>The CHECK constraint {@code chk_shard_reason_consistent} ties each reason to the columns it uses. */
@Entity
@Table(name = "shard_transaction")
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class ShardTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id", nullable = false)
	private Player player;

	/** Negative for PACK_PURCHASE, positive for every bonus. */
	@Column(nullable = false)
	private int amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ShardReason reason;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pack_opening_id", unique = true)
	private PackOpening packOpening;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "invited_player_id", unique = true)
	private Player invitedPlayer;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "season_id")
	private Season season;

	/** Orbit reached, from 2 to 4, for ORBIT_REWARD only. */
	@Column
	private Short orbit;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
}
