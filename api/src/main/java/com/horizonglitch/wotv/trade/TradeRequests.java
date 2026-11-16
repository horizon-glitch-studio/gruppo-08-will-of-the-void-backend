package com.horizonglitch.wotv.trade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Request bodies of the trade routes. */
public final class TradeRequests {

	private TradeRequests() {
	}

	public record CardQuantity(@NotNull(message = "FIELD_REQUIRED") Long cardId,
			@NotNull(message = "FIELD_REQUIRED") Integer quantity) {
	}

	/** One side can be empty (a gift), but the trade moves at least one card (TRADE-04). */
	public record Propose(
			@NotBlank(message = "FIELD_REQUIRED") String recipientUsername,
			List<@Valid @NotNull(message = "FIELD_REQUIRED") CardQuantity> offeredCards,
			List<@Valid @NotNull(message = "FIELD_REQUIRED") CardQuantity> requestedCards) {

		public List<CardQuantity> offered() {
			return offeredCards == null ? List.of() : offeredCards;
		}

		public List<CardQuantity> requested() {
			return requestedCards == null ? List.of() : requestedCards;
		}
	}
}
