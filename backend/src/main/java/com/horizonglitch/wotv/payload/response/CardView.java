package com.horizonglitch.wotv.payload.response;

public record CardView(
		long id,
		String name,
		String description,
		String imageUrl,
		FactionView faction,
		RarityView rarity,
		int attack,
		int defense) {

	public record FactionView(int id, String name) {
	}

	public record RarityView(
			int id,
			String name,
			int order,
			int maxCopiesPerDeck,
			Integer maxPerDeck) {
	}
}
