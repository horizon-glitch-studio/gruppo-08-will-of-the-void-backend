package com.horizonglitch.wotv.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Turns every exception into the common error format. Security errors arrive here too (see security). */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final Clock clock;

	public GlobalExceptionHandler(Clock clock) {
		this.clock = clock;
	}

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiError> handleApi(ApiException e, HttpServletRequest request) {
		return build(e.getStatus(), e.getCode(), e.getMessage(), request, e.getFieldErrors(), e.getDetails());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
		List<ApiError.FieldErrorItem> fields = e.getBindingResult().getFieldErrors().stream()
				.map(f -> new ApiError.FieldErrorItem(f.getField(), f.getDefaultMessage()))
				.toList();
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid.", request, fields);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request body is missing or malformed.", request, List.of());
	}

	/** A query or path parameter that is not of its type, such as an unknown value of an enum. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid.", request,
				List.of(new ApiError.FieldErrorItem(e.getName(), "FIELD_INVALID")));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException e,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid.", request,
				List.of(new ApiError.FieldErrorItem(e.getParameterName(), "FIELD_REQUIRED")));
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiError> handleAuthentication(AuthenticationException e, HttpServletRequest request) {
		return build(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "The access token is missing, malformed or expired.", request, List.of());
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
		return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "This role cannot use this route.", request, List.of());
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException e, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "No route matches this path.", request, List.of());
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
		return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", e.getMessage(), request, List.of());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception e, HttpServletRequest request) {
		log.error("Unexpected error on {}", request.getRequestURI(), e);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error.", request, List.of());
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String code, String message, HttpServletRequest request,
			List<ApiError.FieldErrorItem> fieldErrors) {
		return build(status, code, message, request, fieldErrors, Map.of());
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String code, String message, HttpServletRequest request,
			List<ApiError.FieldErrorItem> fieldErrors, Map<String, Object> details) {
		ApiError body = new ApiError(clock.instant(), status.value(), status.getReasonPhrase(), code, message,
				request.getRequestURI(), fieldErrors, details);
		return ResponseEntity.status(status).body(body);
	}
}
