package com.horizonglitch.wotv.social;

import java.time.Instant;

public final class FriendshipResponses {

	private FriendshipResponses() {
	}

	public record FriendshipView(Long id, String requesterUsername, String addresseeUsername,
			FriendshipStatus status, Instant createdAt, Instant acceptedAt) {

		public static FriendshipView of(Friendship friendship) {
			return new FriendshipView(friendship.getId(),
					friendship.getRequester().getUsername(),
					friendship.getAddressee().getUsername(),
					friendship.getStatus(),
					friendship.getCreatedAt(),
					friendship.getAcceptedAt());
		}
	}
}
