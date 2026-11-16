package com.horizonglitch.wotv.card;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A card of the starter deck, the same for every new Summoner, with its copies. */
@Entity
@Table(name = "starter_deck_card")
@Getter
@Setter
@NoArgsConstructor
public class StarterDeckCard {

	@Id
	@Column(name = "card_id")
	private Long cardId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "card_id")
	private Card card;

	@Column(nullable = false)
	private int quantity;
}
