package com.horizonglitch.wotv.pack;

import com.horizonglitch.wotv.card.CatalogResponses;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Response bodies of the pack routes. */
public final class PackResponses {

	private PackResponses() {
	}

	public record RarityOddsView(Integer rarityId, String rarityName, BigDecimal probability) {
	}

	public record SlotView(int position, List<RarityOddsView> odds) {
	}

	/** A guarantee: the {@code everyPacks}-th pack in a row without that rarity holds one. */
	public record GuaranteeView(GuaranteeType type, int everyPacks) {
	}

	/** A pack type with the odds of every slot and its guarantees; {@code eventCards} are the event's Wanderers. */
	public record PackView(Integer id, String name, PackKind kind, int shardCost, boolean available,
			Instant availableFrom, Instant availableUntil, List<SlotView> slots, List<GuaranteeView> guarantees,
			List<CatalogResponses.CardView> eventCards) {
	}

	public record PackRef(Integer id, String name, PackKind kind) {
	}

	/** {@code expiresAt} is set for Wanderers; {@code isNew} is true when the card was not on board before. */
	public record OpenedCard(CatalogResponses.CardView card, int quantity, Instant expiresAt, boolean isNew) {
	}

	/** The result of a purchase, with the balance after it. */
	public record Opening(Long id, PackRef pack, int shardsSpent, int shardBalance, GuaranteeType guarantee,
			List<OpenedCard> cards, Instant createdAt) {
	}

	public record OpeningSummary(Long id, PackRef pack, int shardsSpent, GuaranteeType guarantee, int totalCards,
			Instant createdAt) {
	}

	public record DrawnCard(CatalogResponses.CardView card, int quantity) {
	}

	public record OpeningDetail(Long id, PackRef pack, int shardsSpent, GuaranteeType guarantee,
			List<DrawnCard> cards, Instant createdAt) {
	}
}
