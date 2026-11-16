package com.horizonglitch.wotv.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Error body of every API response (api-contract-draft/01-auth-profile.md, section 0). {@code details} holds
 * extra values of some errors, such as balance and cost for INSUFFICIENT_SHARDS, and is left out when empty.
 */
public record ApiError(
		Instant timestamp,
		int status,
		String error,
		String code,
		String message,
		String path,
		List<FieldErrorItem> fieldErrors,
		@JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> details) {

	/** A field that failed validation, with a stable code the frontend translates. */
	public record FieldErrorItem(String field, String code) {
	}
}
