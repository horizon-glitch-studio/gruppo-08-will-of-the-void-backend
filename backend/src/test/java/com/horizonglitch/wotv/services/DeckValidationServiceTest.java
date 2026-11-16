package com.horizonglitch.wotv.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.horizonglitch.wotv.payload.response.DeckApiModels.CardSelection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckValidationServiceTest {

	private final DeckValidationService service = new DeckValidationService();

	@Test
	void acceptsThirtyCardsWithinFactionAndRarityLimits() {
		Map<Long, DeckValidationService.CardRule> cards = new HashMap<>();
		List<CardSelection> selections = new ArrayList<>();
		for (long id = 1; id <= 30; id++) {
			int factionId = id <= 15 ? 1 : 2;
			selections.add(new CardSelection(id, 1));
			cards.put(id, rule(factionId, "Soldier", 1, null, 1, true));
		}

		var result = service.validate(selections, cards);

		assertEquals("VALID", result.status());
		assertEquals(30, result.totalCards());
		assertTrue(result.validationErrors().isEmpty());
	}

	@Test
	void marksShortDeckIncomplete() {
		Map<Long, DeckValidationService.CardRule> cards = new HashMap<>();
		List<CardSelection> selections = new ArrayList<>();
		for (long id = 1; id <= 29; id++) {
			selections.add(new CardSelection(id, 1));
			cards.put(id, rule((int) (id % 2) + 1, "Soldier", 1, null, 1, true));
		}

		var result = service.validate(selections, cards);

		assertEquals("INCOMPLETE", result.status());
		assertTrue(result.validationErrors().stream().anyMatch(error -> error.code().equals("DECK_SIZE_INCOMPLETE")));
	}

	@Test
	void invalidatesDeckForFactionLimitAndUnavailableCards() {
		Map<Long, DeckValidationService.CardRule> cards = new HashMap<>();
		List<CardSelection> selections = new ArrayList<>();
		for (long id = 1; id <= 30; id++) {
			selections.add(new CardSelection(id, 1));
			boolean available = id != 30;
			int factionId = id <= 16 ? 1 : 2;
			cards.put(id, rule(factionId, "Soldier", 1, null, 1, available));
		}

		var result = service.validate(selections, cards);

		assertEquals("INVALID", result.status());
		assertTrue(result.validationErrors().stream().anyMatch(error -> error.code().equals("CARD_NOT_OWNED")));
		assertTrue(result.validationErrors().stream().anyMatch(error -> error.code().equals("FACTION_CARD_LIMIT_EXCEEDED")));
	}

	@Test
	void appliesRarityMaximumAndExemptsWanderersFromFactionLimit() {
		Map<Long, DeckValidationService.CardRule> cards = new HashMap<>();
		List<CardSelection> selections = new ArrayList<>();
		for (long id = 1; id <= 15; id++) {
			selections.add(new CardSelection(id, 1));
			cards.put(id, rule(1, "Soldier", 1, 15, 1, true));
		}
		for (long id = 16; id <= 30; id++) {
			selections.add(new CardSelection(id, 1));
			cards.put(id, rule(1, "Wanderer", 2, 15, 1, true));
		}

		var result = service.validate(selections, cards);

		assertEquals("VALID", result.status());
	}

	@Test
	void rejectsCardsWhenRarityMaximumIsExceeded() {
		Map<Long, DeckValidationService.CardRule> cards = new HashMap<>();
		List<CardSelection> selections = new ArrayList<>();
		for (long id = 1; id <= 30; id++) {
			selections.add(new CardSelection(id, 1));
			cards.put(id, rule((int) (id % 2) + 1, "Champion", 2, 20, 1, true));
		}

		var result = service.validate(selections, cards);

		assertEquals("INVALID", result.status());
		assertTrue(result.validationErrors().stream().anyMatch(error -> error.code().equals("RARITY_COPY_LIMIT_EXCEEDED")));
	}

	private DeckValidationService.CardRule rule(
			int factionId,
			String rarityName,
			int rarityId,
			Integer maxPerDeck,
			int ownedQuantity,
			boolean available) {
		return new DeckValidationService.CardRule(
				rarityId,
				rarityName,
				factionId,
				false,
				1,
				maxPerDeck,
				ownedQuantity,
				available);
	}
}
