package com.horizonglitch.wotv.card;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One of the 8 factions: 6 elemental ones and the inert Gem and Noble Gas. */
@Entity
@Table(name = "faction")
@Getter
@Setter
@NoArgsConstructor
public class Faction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false, unique = true, length = 50)
	private String name;

	/** Gem and Noble Gas never give or receive an advantage. */
	@Column(name = "is_inert", nullable = false)
	private boolean inert;
}
