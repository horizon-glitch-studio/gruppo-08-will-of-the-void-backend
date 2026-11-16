package com.horizonglitch.wotv.pack;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.horizonglitch.wotv.player.Player;

/** Guarantee counters of a Summoner, one row per Summoner, locked when a pack is opened. */
@Entity
@Table(name = "pack_guarantee")
@Getter
@Setter
@NoArgsConstructor
public class PackGuarantee {

	@Id
	@Column(name = "player_id")
	private Long playerId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id")
	private Player player;

	@Column(name = "packs_without_champion", nullable = false)
	private short packsWithoutChampion;

	@Column(name = "standard_packs_without_elect", nullable = false)
	private short standardPacksWithoutElect;

	@Column(name = "event_packs_without_wanderer", nullable = false)
	private short eventPacksWithoutWanderer;
}
