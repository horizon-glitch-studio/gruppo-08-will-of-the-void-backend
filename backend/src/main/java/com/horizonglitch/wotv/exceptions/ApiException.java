package com.horizonglitch.wotv.exceptions;

import java.util.List;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final List<FieldViolation> fieldErrors;

	public ApiException(HttpStatus status, String code, String message) {
		this(status, code, message, List.of());
	}

	public ApiException(
			HttpStatus status,
			String code,
			String message,
			List<FieldViolation> fieldErrors) {
		super(message);
		this.status = status;
		this.code = code;
		this.fieldErrors = List.copyOf(fieldErrors);
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public List<FieldViolation> getFieldErrors() {
		return fieldErrors;
	}

	public record FieldViolation(String field, String code) {
	}
}
