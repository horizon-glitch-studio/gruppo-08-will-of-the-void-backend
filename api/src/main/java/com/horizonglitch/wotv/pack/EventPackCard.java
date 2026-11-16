package com.horizonglitch.wotv.pack;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.horizonglitch.wotv.card.Card;

/** A Wanderer card that an event pack can contain. */
@Entity
@Table(name = "event_pack_card")
@Getter
@Setter
@NoArgsConstructor
public class EventPackCard {

	@EmbeddedId
	private EventPackCardId id = new EventPackCardId();

	@MapsId("packTypeId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pack_type_id")
	private PackType packType;

	@MapsId("cardId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "card_id")
	private Card card;
}
