package com.horizonglitch.wotv.trade;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Primary key of {@link TradeCard}. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TradeCardId implements Serializable {

	@Column(name = "trade_id")
	private Long tradeId;

	@Column(name = "card_id")
	private Long cardId;

	@Enumerated(EnumType.STRING)
	@Column(name = "side", length = 8)
	private TradeSide side;
}
