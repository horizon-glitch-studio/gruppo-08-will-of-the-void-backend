package com.horizonglitch.wotv.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import com.horizonglitch.wotv.battle.Battle;
import com.horizonglitch.wotv.card.Card;
import com.horizonglitch.wotv.player.Player;
import com.horizonglitch.wotv.social.Friendship;
import com.horizonglitch.wotv.trade.Trade;

/** Something that happened while the Summoner was away. At most one of the linked rows is set. */
@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id", nullable = false)
	private Player player;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private NotificationType type;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "battle_id")
	private Battle battle;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "trade_id")
	private Trade trade;

	/** The Wanderer that left the ship. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "card_id")
	private Card card;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "friendship_id")
	private Friendship friendship;

	@Column(name = "is_read", nullable = false)
	private boolean read;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
}
