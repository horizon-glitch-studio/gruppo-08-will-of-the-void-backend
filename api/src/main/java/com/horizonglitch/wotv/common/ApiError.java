package com.horizonglitch.wotv.common;

import java.time.Instant;
import java.util.List;

/** Error body of every API response (api-contract-draft/01-auth-profile.md, section 0). */
public record ApiError(
		Instant timestamp,
		int status,
		String error,
		String code,
		String message,
		String path,
		List<FieldErrorItem> fieldErrors) {

	/** A field that failed validation, with a stable code the frontend translates. */
	public record FieldErrorItem(String field, String code) {
	}
}
