package com.horizonglitch.wotv.collection;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import com.horizonglitch.wotv.card.Card;
import com.horizonglitch.wotv.player.Player;

/** A stay of a Wanderer that has left the ship: visible, not playable. */
@Entity
@Table(name = "memory_archive")
@Getter
@Setter
@NoArgsConstructor
public class MemoryArchive {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id", nullable = false)
	private Player player;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "card_id", nullable = false)
	private Card card;

	@Column(name = "arrived_at", nullable = false)
	private Instant arrivedAt;

	@Column(name = "departed_at", nullable = false)
	private Instant departedAt;
}
