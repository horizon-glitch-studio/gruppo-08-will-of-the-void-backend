package com.horizonglitch.wotv.card;

import java.util.List;

/** What the API shows of the catalog. Rarity points are an internal design rule and are never exposed (CARD-04). */
public final class CatalogResponses {

	private CatalogResponses() {
	}

	public record FactionView(Integer id, String name, boolean inert) {

		public static FactionView of(Faction faction) {
			return new FactionView(faction.getId(), faction.getName(), faction.isInert());
		}
	}

	/** {@code maxPerDeck} and {@code durationDays} are null when there is no limit. */
	public record RarityView(Integer id, String name, int sortOrder, int maxCopiesPerDeck, Integer maxPerDeck,
			Integer durationDays, boolean tradeable) {

		public static RarityView of(Rarity rarity) {
			return new RarityView(rarity.getId(), rarity.getName(), rarity.getSortOrder(),
					rarity.getMaxCopiesPerDeck(), rarity.getMaxPerDeck(), rarity.getDurationDays(),
					rarity.isTradeable());
		}
	}

	public record CardView(Long id, String name, String description, String imageUrl, FactionView faction,
			RarityView rarity, int attack, int defense) {

		public static CardView of(Card card) {
			return new CardView(card.getId(), card.getName(), card.getDescription(), card.getImageUrl(),
					FactionView.of(card.getFaction()), RarityView.of(card.getRarity()), card.getAttack(),
					card.getDefense());
		}
	}

	/** The dominant faction gets {@code value} extra ATK against the dominated one. */
	public record AdvantageView(Integer dominantFactionId, Integer dominatedFactionId, AdvantageType type,
			int value) {

		public static AdvantageView of(FactionAdvantage advantage) {
			return new AdvantageView(advantage.getId().getDominantFactionId(),
					advantage.getId().getDominatedFactionId(), advantage.getType(), advantage.getValue());
		}
	}

	public record DeckRulesView(int deckSize, int maxPerElementalFaction, int maxSavedDecks) {
	}

	public record GameData(List<FactionView> factions, List<RarityView> rarities, List<AdvantageView> advantages,
			DeckRulesView deckRules) {
	}
}
