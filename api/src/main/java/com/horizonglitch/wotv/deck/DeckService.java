package com.horizonglitch.wotv.deck;

import com.horizonglitch.wotv.card.StarterDeckCard;
import com.horizonglitch.wotv.player.Player;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Decks saved by the Summoners. */
@Service
public class DeckService {

	static final String STARTER_DECK_NAME = "Starter deck";

	private final DeckRepository decks;

	public DeckService(DeckRepository decks) {
		this.decks = decks;
	}

	/** Saves the starter deck for a new Summoner. It is valid by rule (DECK-12), checked by the backoffice. */
	@Transactional(propagation = Propagation.MANDATORY)
	public Deck createStarterDeck(Player player, List<StarterDeckCard> starterDeck) {
		Deck deck = new Deck();
		deck.setPlayer(player);
		deck.setName(STARTER_DECK_NAME);
		deck.setStatus(DeckStatus.VALID);
		for (StarterDeckCard starter : starterDeck) {
			DeckCard card = new DeckCard();
			card.setDeck(deck);
			card.setCard(starter.getCard());
			card.setQuantity(starter.getQuantity());
			deck.getCards().add(card);
		}
		return decks.save(deck);
	}
}
