package com.horizonglitch.wotv.player;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request bodies of the profile routes. In PATCH requests a missing field means "do not change". */
public final class ProfileRequests {

	private ProfileRequests() {
	}

	/** {@code avatar} is the key of a preset avatar, such as {@code avatar_07}. */
	public record UpdateProfile(String avatar, String timezone) {
	}

	public record UpdateSettings(Boolean reduceMotion, Boolean reduceTransparency, Boolean showTextLabels) {
	}

	public record ChangePassword(
			@NotBlank(message = "FIELD_REQUIRED") String currentPassword,

			@NotBlank(message = "FIELD_REQUIRED")
			@Size(min = 8, message = "PASSWORD_TOO_SHORT")
			@Size(max = 72, message = "PASSWORD_TOO_LONG")
			String newPassword) {
	}

	public record DeleteAccount(@NotBlank(message = "FIELD_REQUIRED") String password) {
	}
}
