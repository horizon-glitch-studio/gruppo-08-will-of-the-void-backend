package com.horizonglitch.wotv.payload.response;

import java.time.Instant;

public record CollectionCardView(
		CardView card,
		int quantity,
		Instant acquiredAt,
		Instant expiresAt,
		boolean availableInDeck) {
}
