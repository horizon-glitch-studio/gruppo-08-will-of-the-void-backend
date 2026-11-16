package com.horizonglitch.wotv.trade;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import com.horizonglitch.wotv.player.Player;

/** A trade between two Summoners, to be accepted within 48 hours. */
@Entity
@Table(name = "trade")
@Getter
@Setter
@NoArgsConstructor
public class Trade {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "proposer_id", nullable = false)
	private Player proposer;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recipient_id", nullable = false)
	private Player recipient;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private TradeStatus status = TradeStatus.PENDING;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	/** Set exactly when the trade is no longer PENDING. */
	@Column(name = "closed_at")
	private Instant closedAt;

	@OneToMany(mappedBy = "trade", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<TradeCard> cards = new ArrayList<>();
}
