package com.horizonglitch.wotv.collection;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import com.horizonglitch.wotv.card.Card;
import com.horizonglitch.wotv.player.Player;

/** Copies of a card owned by a Summoner. A Wanderer has quantity 1 and an {@code expiresAt}. */
@Entity
@Table(name = "collection_card")
@Getter
@Setter
@NoArgsConstructor
public class CollectionCard {

	@EmbeddedId
	private CollectionCardId id = new CollectionCardId();

	@MapsId("playerId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id")
	private Player player;

	@MapsId("cardId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "card_id")
	private Card card;

	@Column(nullable = false)
	private int quantity;

	@CreationTimestamp
	@Column(name = "acquired_at", nullable = false, updatable = false)
	private Instant acquiredAt;

	/** Departure of a Wanderer; empty for permanent cards. */
	@Column(name = "expires_at")
	private Instant expiresAt;
}
