package com.horizonglitch.wotv.card;

import java.util.List;
import org.springframework.stereotype.Service;

/** The starter deck, the same for every new Summoner (DECK-11). */
@Service
public class StarterDeckService {

	private final StarterDeckCardRepository starterDeck;

	public StarterDeckService(StarterDeckCardRepository starterDeck) {
		this.starterDeck = starterDeck;
	}

	/** Empty until the card set is loaded by a data migration. */
	public List<StarterDeckCard> getStarterDeck() {
		return starterDeck.findAllWithCards();
	}
}
