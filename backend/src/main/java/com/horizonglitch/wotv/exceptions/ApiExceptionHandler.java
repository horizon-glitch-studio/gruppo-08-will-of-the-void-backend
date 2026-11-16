package com.horizonglitch.wotv.exceptions;

import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice(assignableTypes = {
		com.horizonglitch.wotv.controllers.CollectionController.class,
		com.horizonglitch.wotv.controllers.DeckController.class
})
public class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiError> handleApiException(ApiException exception, HttpServletRequest request) {
		return ResponseEntity.status(exception.getStatus()).body(new ApiError(
				Instant.now(),
				exception.getStatus().value(),
				exception.getStatus().getReasonPhrase(),
				exception.getCode(),
				exception.getMessage(),
				request.getRequestURI(),
				exception.getFieldErrors().stream()
						.map(error -> new FieldErrorItem(error.field(), error.code()))
						.toList()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleInvalidBody(
			MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		List<FieldErrorItem> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
				.map(this::toFieldError)
				.distinct()
				.toList();
		return validationError(request, fieldErrors);
	}

	@ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
	public ResponseEntity<ApiError> handleInvalidParameters(Exception exception, HttpServletRequest request) {
		return validationError(request, List.of());
	}

	private ResponseEntity<ApiError> validationError(HttpServletRequest request, List<FieldErrorItem> fieldErrors) {
		return ResponseEntity.badRequest().body(new ApiError(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				HttpStatus.BAD_REQUEST.getReasonPhrase(),
				"VALIDATION_FAILED",
				"One or more fields are invalid.",
				request.getRequestURI(),
				fieldErrors));
	}

	private FieldErrorItem toFieldError(FieldError error) {
		String code = switch (error.getCode() == null ? "" : error.getCode()) {
			case "NotBlank", "NotEmpty" -> "FIELD_REQUIRED";
			case "Size" -> "FIELD_LENGTH_INVALID";
			case "Positive" -> "VALUE_MUST_BE_POSITIVE";
			case "NotNull" -> "FIELD_REQUIRED";
			default -> "FIELD_INVALID";
		};
		return new FieldErrorItem(error.getField(), code);
	}

	public record FieldErrorItem(String field, String code) {
	}

	public record ApiError(
			Instant timestamp,
			int status,
			String error,
			String code,
			String message,
			String path,
			List<FieldErrorItem> fieldErrors) {
	}
}
