package com.horizonglitch.wotv.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Primary key of {@link CollectionCard}. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CollectionCardId implements Serializable {

	@Column(name = "player_id")
	private Long playerId;

	@Column(name = "card_id")
	private Long cardId;
}
