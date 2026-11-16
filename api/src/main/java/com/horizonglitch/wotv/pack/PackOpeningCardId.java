package com.horizonglitch.wotv.pack;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Primary key of {@link PackOpeningCard}. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PackOpeningCardId implements Serializable {

	@Column(name = "pack_opening_id")
	private Long packOpeningId;

	@Column(name = "card_id")
	private Long cardId;
}
