package com.horizonglitch.wotv.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import com.horizonglitch.wotv.payload.response.DeckApiModels.CardSelection;
import com.horizonglitch.wotv.payload.response.DeckApiModels.ValidationError;
import com.horizonglitch.wotv.payload.response.DeckApiModels.ValidationResult;

@Service
public class DeckValidationService {

	public static final int DECK_SIZE = 30;
	public static final int MAX_CARDS_PER_FACTION = 15;

	public ValidationResult validate(List<CardSelection> selections, Map<Long, CardRule> cardsById) {
		List<ValidationError> errors = new ArrayList<>();
		long totalCards = 0;
		Map<Integer, Long> countsByRarity = new HashMap<>();
		Map<Integer, Long> countsByFaction = new HashMap<>();

		for (CardSelection selection : selections) {
			long cardId = selection.cardId();
			int quantity = selection.quantity();
			totalCards += quantity;

			CardRule card = cardsById.get(cardId);
			if (card == null) {
				errors.add(new ValidationError("CARD_NOT_FOUND", "cards", cardId, null, null, null));
				continue;
			}

			if (!card.available()) {
				errors.add(new ValidationError("CARD_NOT_OWNED", "cards", cardId, null, (long) quantity, 0L));
			} else if (quantity > card.ownedQuantity()) {
				errors.add(new ValidationError(
						"CARD_QUANTITY_EXCEEDED",
						"cards",
						cardId,
						null,
						(long) quantity,
						(long) card.ownedQuantity()));
			}

			if (quantity > card.maxCopiesPerDeck()) {
				errors.add(new ValidationError(
						"CARD_COPY_LIMIT_EXCEEDED",
						"cards",
						cardId,
						card.rarityId(),
						(long) quantity,
						(long) card.maxCopiesPerDeck()));
			}

			countsByRarity.merge(
					card.rarityId(),
					(long) quantity,
					(@NonNull Long current, @NonNull Long added) -> Long.sum(current, added));
			if (!card.factionIsInert() && !isRarityExcludedFromFactionLimit(card.rarityName())) {
				countsByFaction.merge(
						card.factionId(),
						(long) quantity,
						(@NonNull Long current, @NonNull Long added) -> Long.sum(current, added));
			}
		}

		for (Map.Entry<Integer, Long> entry : countsByRarity.entrySet()) {
			CardRule representative = cardsById.values().stream()
					.filter(card -> card.rarityId() == entry.getKey())
					.findFirst()
					.orElseThrow();
			Integer maxPerDeck = representative.maxPerDeck();
			if (maxPerDeck != null && entry.getValue() > maxPerDeck) {
				errors.add(new ValidationError(
						"RARITY_COPY_LIMIT_EXCEEDED",
						"cards",
						null,
						entry.getKey(),
						entry.getValue(),
						maxPerDeck.longValue()));
			}
		}

		for (Map.Entry<Integer, Long> entry : countsByFaction.entrySet()) {
			if (entry.getValue() > MAX_CARDS_PER_FACTION) {
				errors.add(new ValidationError(
						"FACTION_CARD_LIMIT_EXCEEDED",
						"cards",
						null,
						null,
						entry.getValue(),
						(long) MAX_CARDS_PER_FACTION));
			}
		}

		boolean invalidForRules = !errors.isEmpty();
		if (totalCards < DECK_SIZE) {
			errors.add(new ValidationError(
					"DECK_SIZE_INCOMPLETE",
					"cards",
					null,
					null,
					totalCards,
					(long) DECK_SIZE));
			return new ValidationResult(invalidForRules ? "INVALID" : "INCOMPLETE", totalCards, List.copyOf(errors));
		}
		if (totalCards > DECK_SIZE) {
			errors.add(new ValidationError(
					"DECK_SIZE_EXCEEDED",
					"cards",
					null,
					null,
					totalCards,
					(long) DECK_SIZE));
			return new ValidationResult("INVALID", totalCards, List.copyOf(errors));
		}
		return new ValidationResult(invalidForRules ? "INVALID" : "VALID", totalCards, List.copyOf(errors));
	}

	private boolean isRarityExcludedFromFactionLimit(String rarityName) {
		return rarityName.equalsIgnoreCase("Wanderer") || rarityName.equalsIgnoreCase("Elect");
	}

	public record CardRule(
			int rarityId,
			String rarityName,
			int factionId,
			boolean factionIsInert,
			int maxCopiesPerDeck,
			Integer maxPerDeck,
			int ownedQuantity,
			boolean available) {
	}
}
