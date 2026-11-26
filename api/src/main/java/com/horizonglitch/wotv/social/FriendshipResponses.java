package com.horizonglitch.wotv.social;

import java.time.Instant;

public final class FriendshipResponses {
	private FriendshipResponses() {}

	public record Friend(long friendshipId, long playerId, String username, String avatar, int trophies,
			Instant since) {}

	public record Request(long friendshipId, long playerId, String username, String avatar,
			Instant createdAt, String direction) {}
}
