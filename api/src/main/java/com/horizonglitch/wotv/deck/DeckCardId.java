package com.horizonglitch.wotv.deck;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Primary key of {@link DeckCard}. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DeckCardId implements Serializable {

	@Column(name = "deck_id")
	private Long deckId;

	@Column(name = "card_id")
	private Long cardId;
}
