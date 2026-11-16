package com.horizonglitch.wotv.common;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** A business error with its HTTP status and stable code, turned into an {@link ApiError}. */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final List<ApiError.FieldErrorItem> fieldErrors;
	private final Map<String, Object> details;

	public ApiException(HttpStatus status, String code, String message) {
		this(status, code, message, List.of());
	}

	public ApiException(HttpStatus status, String code, String message, List<ApiError.FieldErrorItem> fieldErrors) {
		super(message);
		this.status = status;
		this.code = code;
		this.fieldErrors = List.copyOf(fieldErrors);
		this.details = Map.of();
	}

	/** An error with extra values in {@code details}. */
	public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
		super(message);
		this.status = status;
		this.code = code;
		this.fieldErrors = List.of();
		this.details = Map.copyOf(details);
	}

	/** 400 VALIDATION_FAILED for checks that annotations cannot express. */
	public static ApiException validation(List<ApiError.FieldErrorItem> fieldErrors) {
		return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid.", fieldErrors);
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public List<ApiError.FieldErrorItem> getFieldErrors() {
		return fieldErrors;
	}

	public Map<String, Object> getDetails() {
		return details;
	}
}
