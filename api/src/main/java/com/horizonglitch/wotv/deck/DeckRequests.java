package com.horizonglitch.wotv.deck;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request bodies of the deck routes. */
public final class DeckRequests {

	private DeckRequests() {
	}

	public record CardQuantity(
			@NotNull(message = "FIELD_REQUIRED") Long cardId,
			@NotNull(message = "FIELD_REQUIRED") @Min(value = 1, message = "QUANTITY_INVALID") Integer quantity) {
	}

	/** Create or replace a deck: the name and the whole card list. */
	public record Save(
			@NotBlank(message = "FIELD_REQUIRED") @Size(max = 100, message = "DECK_NAME_TOO_LONG") String name,
			@NotNull(message = "FIELD_REQUIRED") List<@Valid @NotNull(message = "FIELD_REQUIRED") CardQuantity> cards) {
	}

	/** Check a composition without saving it. */
	public record Validate(
			@NotNull(message = "FIELD_REQUIRED") List<@Valid @NotNull(message = "FIELD_REQUIRED") CardQuantity> cards) {
	}
}
