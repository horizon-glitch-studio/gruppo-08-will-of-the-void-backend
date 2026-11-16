package com.horizonglitch.wotv.collection;

import com.horizonglitch.wotv.card.CatalogResponses;
import java.time.Instant;

/** Response bodies of the collection routes. */
public final class CollectionResponses {

	private CollectionResponses() {
	}

	/** {@code expiresAt} is null for permanent cards; a Wanderer has quantity 1 and a departure time. */
	public record Item(CatalogResponses.CardView card, int quantity, Instant acquiredAt, Instant expiresAt) {

		public static Item of(CollectionCard owned) {
			return new Item(CatalogResponses.CardView.of(owned.getCard()), owned.getQuantity(), owned.getAcquiredAt(),
					owned.getExpiresAt());
		}
	}

	/** A stay of a Wanderer that has left: visible, not playable. */
	public record ArchiveItem(Long id, CatalogResponses.CardView card, Instant arrivedAt, Instant departedAt) {

		public static ArchiveItem of(MemoryArchive archived) {
			return new ArchiveItem(archived.getId(), CatalogResponses.CardView.of(archived.getCard()),
					archived.getArrivedAt(), archived.getDepartedAt());
		}
	}
}
