package com.horizonglitch.wotv.card;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Primary key of {@link FactionAdvantage}. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FactionAdvantageId implements Serializable {

	@Column(name = "dominant_faction_id")
	private Integer dominantFactionId;

	@Column(name = "dominated_faction_id")
	private Integer dominatedFactionId;
}
