package com.horizonglitch.wotv.trade;

import com.horizonglitch.wotv.card.CatalogResponses;
import java.time.Instant;
import java.util.List;

/** Response bodies of the trade routes. */
public final class TradeResponses {

	private TradeResponses() {
	}

	public record PlayerRef(Long id, String username) {
	}

	public record TradeCardView(CatalogResponses.CardView card, int quantity) {
	}

	/** {@code offeredCards} go from proposer to recipient, {@code requestedCards} from recipient to proposer. */
	public record TradeView(Long id, TradeStatus status, PlayerRef proposer, PlayerRef recipient,
			List<TradeCardView> offeredCards, List<TradeCardView> requestedCards, Instant createdAt,
			Instant expiresAt, Instant closedAt) {
	}
}
