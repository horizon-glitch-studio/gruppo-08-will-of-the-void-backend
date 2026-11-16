package com.horizonglitch.wotv.card;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Rarity of a card: points, deck limits, duration and whether it can be traded. */
@Entity
@Table(name = "rarity")
@Getter
@Setter
@NoArgsConstructor
public class Rarity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false, unique = true, length = 50)
	private String name;

	@Column(name = "sort_order", nullable = false, unique = true)
	private int sortOrder;

	/** ATK + DEF of every card of this rarity. */
	@Column(name = "total_points", nullable = false)
	private int totalPoints;

	@Column(name = "max_copies_per_deck", nullable = false)
	private int maxCopiesPerDeck;

	/** Maximum cards of this rarity in a deck; empty means no limit. */
	@Column(name = "max_per_deck")
	private Integer maxPerDeck;

	/** Days a card stays on board (Wanderer); empty means permanent. */
	@Column(name = "duration_days")
	private Integer durationDays;

	@Column(name = "is_tradeable", nullable = false)
	private boolean tradeable = true;
}
