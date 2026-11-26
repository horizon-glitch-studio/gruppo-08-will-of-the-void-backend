package com.horizonglitch.wotv.social;

import jakarta.validation.constraints.NotBlank;

public final class FriendshipRequests {

	private FriendshipRequests() {
	}

	public record Send(@NotBlank(message = "FIELD_REQUIRED") String username) {
	}
}
