package com.horizonglitch.wotv.trade;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.horizonglitch.wotv.card.Card;

/** Copies of a card offered or requested in a trade. */
@Entity
@Table(name = "trade_card")
@Getter
@Setter
@NoArgsConstructor
public class TradeCard {

	@EmbeddedId
	private TradeCardId id = new TradeCardId();

	@MapsId("tradeId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trade_id")
	private Trade trade;

	@MapsId("cardId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "card_id")
	private Card card;

	@Column(nullable = false)
	private int quantity;
}
