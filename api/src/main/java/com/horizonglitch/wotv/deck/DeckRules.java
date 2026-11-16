package com.horizonglitch.wotv.deck;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The deck constraints of rules/decks.md (DECK-01 to DECK-05), as a pure function: it receives the cards of a
 * deck with everything it needs and returns the status and every rule broken. No database, no Spring.
 */
public final class DeckRules {

	private DeckRules() {
	}

	/**
	 * A card of the deck. {@code rarityMaxPerDeck} is null when the rarity has no limit; {@code owned} is the
	 * number of copies in the collection; {@code playable} is false for a Wanderer whose time has ended.
	 */
	public record Entry(long cardId, int quantity, int factionId, boolean inertFaction, int rarityId,
			int rarityMaxCopies, Integer rarityMaxPerDeck, int owned, boolean playable) {
	}

	/** A rule broken: {@code limit} is the maximum allowed and {@code actual} the value found. */
	public record Violation(String code, Long cardId, Integer rarityId, Integer factionId, int limit, int actual) {

		static Violation ofDeck(String code, int limit, int actual) {
			return new Violation(code, null, null, null, limit, actual);
		}
	}

	public record Result(DeckStatus status, int totalCards, List<Violation> violations) {
	}

	/** Codes of {@link Violation}. */
	public static final String DECK_SIZE = "DECK_SIZE";
	public static final String TOO_MANY_COPIES = "TOO_MANY_COPIES";
	public static final String RARITY_LIMIT = "RARITY_LIMIT";
	public static final String FACTION_LIMIT = "FACTION_LIMIT";
	public static final String NOT_ENOUGH_COPIES = "NOT_ENOUGH_COPIES";
	public static final String CARD_NOT_PLAYABLE = "CARD_NOT_PLAYABLE";

	/**
	 * VALID with exactly {@code deckSize} cards and no rule broken; INCOMPLETE with fewer cards and no other rule
	 * broken; INVALID otherwise.
	 */
	public static Result check(List<Entry> entries, int deckSize, int maxPerElementalFaction) {
		List<Violation> violations = new ArrayList<>();
		int total = 0;
		Map<Integer, Integer> perRarity = new LinkedHashMap<>();
		Map<Integer, Integer> rarityLimits = new LinkedHashMap<>();
		Map<Integer, Integer> perFaction = new LinkedHashMap<>();

		for (Entry e : entries) {
			total += e.quantity();
			if (e.quantity() > e.rarityMaxCopies()) {
				violations.add(new Violation(TOO_MANY_COPIES, e.cardId(), null, null, e.rarityMaxCopies(), e.quantity()));
			}
			if (e.quantity() > e.owned()) {
				violations.add(new Violation(NOT_ENOUGH_COPIES, e.cardId(), null, null, e.owned(), e.quantity()));
			}
			if (!e.playable()) {
				violations.add(new Violation(CARD_NOT_PLAYABLE, e.cardId(), null, null, 0, e.quantity()));
			}
			perRarity.merge(e.rarityId(), e.quantity(), Integer::sum);
			if (e.rarityMaxPerDeck() != null) {
				rarityLimits.put(e.rarityId(), e.rarityMaxPerDeck());
			}
			if (!e.inertFaction()) {
				perFaction.merge(e.factionId(), e.quantity(), Integer::sum);
			}
		}
		rarityLimits.forEach((rarityId, limit) -> {
			int actual = perRarity.get(rarityId);
			if (actual > limit) {
				violations.add(new Violation(RARITY_LIMIT, null, rarityId, null, limit, actual));
			}
		});
		perFaction.forEach((factionId, actual) -> {
			if (actual > maxPerElementalFaction) {
				violations.add(new Violation(FACTION_LIMIT, null, null, factionId, maxPerElementalFaction, actual));
			}
		});
		if (total != deckSize) {
			violations.add(Violation.ofDeck(DECK_SIZE, deckSize, total));
		}

		boolean onlyTooFew = violations.stream().allMatch(v -> v.code().equals(DECK_SIZE)) && total < deckSize;
		DeckStatus status = violations.isEmpty() ? DeckStatus.VALID
				: onlyTooFew ? DeckStatus.INCOMPLETE : DeckStatus.INVALID;
		return new Result(status, total, List.copyOf(violations));
	}
}
