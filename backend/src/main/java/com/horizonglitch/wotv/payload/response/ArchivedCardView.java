package com.horizonglitch.wotv.payload.response;

import java.time.Instant;

public record ArchivedCardView(
		long id,
		CardView card,
		Instant arrivedAt,
		Instant departedAt) {
}
