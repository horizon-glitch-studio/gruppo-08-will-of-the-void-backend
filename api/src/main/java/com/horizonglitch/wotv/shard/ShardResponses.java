package com.horizonglitch.wotv.shard;

import java.time.Instant;

/** Response bodies of the Shard routes. */
public final class ShardResponses {

	private ShardResponses() {
	}

	/** A Shard income (positive) or expense (negative), with the row it comes from when there is one. */
	public record Transaction(Long id, int amount, ShardReason reason, Long packOpeningId, Long invitedPlayerId,
			Integer seasonId, Short orbit, Instant createdAt) {

		static Transaction of(ShardTransaction t) {
			return new Transaction(t.getId(), t.getAmount(), t.getReason(),
					t.getPackOpening() != null ? t.getPackOpening().getId() : null,
					t.getInvitedPlayer() != null ? t.getInvitedPlayer().getId() : null,
					t.getSeason() != null ? t.getSeason().getId() : null, t.getOrbit(), t.getCreatedAt());
		}
	}
}
