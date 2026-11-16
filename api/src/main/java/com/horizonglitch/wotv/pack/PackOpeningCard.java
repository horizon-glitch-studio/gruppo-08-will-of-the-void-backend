package com.horizonglitch.wotv.pack;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.horizonglitch.wotv.card.Card;

/** Copies of a card drawn in a pack opening. */
@Entity
@Table(name = "pack_opening_card")
@Getter
@Setter
@NoArgsConstructor
public class PackOpeningCard {

	@EmbeddedId
	private PackOpeningCardId id = new PackOpeningCardId();

	@MapsId("packOpeningId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pack_opening_id")
	private PackOpening packOpening;

	@MapsId("cardId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "card_id")
	private Card card;

	@Column(nullable = false)
	private int quantity;
}
