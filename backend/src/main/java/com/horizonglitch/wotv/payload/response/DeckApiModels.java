package com.horizonglitch.wotv.payload.response;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class DeckApiModels {

	private DeckApiModels() {
	}

	public record CardSelection(
			@NotNull @Positive Long cardId,
			@NotNull @Positive Integer quantity) {
	}

	public record SaveDeckRequest(
			@NotBlank @Size(max = 100) String name,
			@NotNull List<@Valid CardSelection> cards) {
	}

	public record ValidateDeckRequest(@NotNull List<@Valid CardSelection> cards) {
	}

	public record DeckCardView(CardView card, int quantity) {
	}

	public record ValidationError(
			String code,
			String field,
			Long cardId,
			Integer rarityId,
			Long currentValue,
			Long maximumValue) {
	}

	public record ValidationResult(
			String status,
			long totalCards,
			List<ValidationError> validationErrors) {
	}

	public record DeckView(
			long id,
			String name,
			String status,
			List<DeckCardView> cards,
			long totalCards,
			boolean isDefenseDeck,
			Instant updatedAt,
			List<ValidationError> validationErrors) {
	}
}
