package com.horizonglitch.wotv.card;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import org.hibernate.annotations.UpdateTimestamp;

/** A card of the catalog. */
@Entity
@Table(name = "card")
@Getter
@Setter
@NoArgsConstructor
public class Card {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 100)
	private String name;

	@Column(columnDefinition = "text")
	private String description;

	@Column(name = "image_url")
	private String imageUrl;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "faction_id", nullable = false)
	private Faction faction;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "rarity_id", nullable = false)
	private Rarity rarity;

	@Column(nullable = false)
	private short attack;

	@Column(nullable = false)
	private short defense;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
