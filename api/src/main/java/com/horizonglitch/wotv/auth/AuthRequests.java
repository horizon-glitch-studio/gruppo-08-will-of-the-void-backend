package com.horizonglitch.wotv.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request bodies of the auth routes. Validation messages are the stable codes of
 * {@code fieldErrors[].code} in the API contract.
 */
public final class AuthRequests {

	private AuthRequests() {
	}

	public record Register(
			@NotBlank(message = "FIELD_REQUIRED")
			@Size(min = 3, message = "USERNAME_TOO_SHORT")
			@Size(max = 20, message = "USERNAME_TOO_LONG")
			@Pattern(regexp = "^[A-Za-z0-9_]*$", message = "USERNAME_INVALID_CHARACTERS")
			String username,

			@NotBlank(message = "FIELD_REQUIRED")
			@Email(message = "EMAIL_INVALID")
			@Size(max = 255, message = "EMAIL_INVALID")
			String email,

			@NotBlank(message = "FIELD_REQUIRED")
			@Size(min = 8, message = "PASSWORD_TOO_SHORT")
			@Size(max = 72, message = "PASSWORD_TOO_LONG")
			String password,

			@NotBlank(message = "FIELD_REQUIRED")
			String timezone,

			String inviteCode) {
	}

	public record Login(
			@NotBlank(message = "FIELD_REQUIRED") String identifier,
			@NotBlank(message = "FIELD_REQUIRED") String password) {
	}

	public record RefreshToken(@NotBlank(message = "FIELD_REQUIRED") String refreshToken) {
	}

	public record ActionToken(@NotBlank(message = "FIELD_REQUIRED") String token) {
	}

	public record ForgotPassword(
			@NotBlank(message = "FIELD_REQUIRED")
			@Email(message = "EMAIL_INVALID")
			String email) {
	}

	public record ResetPassword(
			@NotBlank(message = "FIELD_REQUIRED") String token,

			@NotBlank(message = "FIELD_REQUIRED")
			@Size(min = 8, message = "PASSWORD_TOO_SHORT")
			@Size(max = 72, message = "PASSWORD_TOO_LONG")
			String newPassword) {
	}
}
