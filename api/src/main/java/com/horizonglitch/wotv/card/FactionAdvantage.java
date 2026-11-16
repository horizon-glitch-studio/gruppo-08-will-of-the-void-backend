package com.horizonglitch.wotv.card;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The dominant faction gets {@code value} extra ATK against the dominated one. 12 rows. */
@Entity
@Table(name = "faction_advantage")
@Getter
@Setter
@NoArgsConstructor
public class FactionAdvantage {

	@EmbeddedId
	private FactionAdvantageId id = new FactionAdvantageId();

	@MapsId("dominantFactionId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "dominant_faction_id")
	private Faction dominantFaction;

	@MapsId("dominatedFactionId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "dominated_faction_id")
	private Faction dominatedFaction;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 8)
	private AdvantageType type = AdvantageType.ATTACK;

	@Column(nullable = false)
	private int value = 3;
}
