package com.horizonglitch.wotv.collection;

import com.horizonglitch.wotv.card.StarterDeckCard;
import com.horizonglitch.wotv.player.Player;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The cards on board each Summoner's ship. */
@Service
public class CollectionService {

	private final CollectionCardRepository collection;

	public CollectionService(CollectionCardRepository collection) {
		this.collection = collection;
	}

	/** Gives a new Summoner the cards of the starter deck, which are permanent. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void addStarterCards(Player player, List<StarterDeckCard> starterDeck) {
		for (StarterDeckCard starter : starterDeck) {
			CollectionCard card = new CollectionCard();
			card.setPlayer(player);
			card.setCard(starter.getCard());
			card.setQuantity(starter.getQuantity());
			collection.save(card);
		}
	}
}
