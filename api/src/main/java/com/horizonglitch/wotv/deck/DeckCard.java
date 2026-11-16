package com.horizonglitch.wotv.deck;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.horizonglitch.wotv.card.Card;

/** Copies of a card in a deck. */
@Entity
@Table(name = "deck_card")
@Getter
@Setter
@NoArgsConstructor
public class DeckCard {

	@EmbeddedId
	private DeckCardId id = new DeckCardId();

	@MapsId("deckId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "deck_id")
	private Deck deck;

	@MapsId("cardId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "card_id")
	private Card card;

	@Column(nullable = false)
	private int quantity;
}
