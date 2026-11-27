package com.horizonglitch.wotv.social;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class FriendshipRequests {
	private FriendshipRequests() {}

	public record Create(@NotBlank @Size(max = 50) String username) {}
}
