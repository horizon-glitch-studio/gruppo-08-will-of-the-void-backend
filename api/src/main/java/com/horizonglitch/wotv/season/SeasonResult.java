package com.horizonglitch.wotv.season;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.horizonglitch.wotv.player.Player;

/** Trophies of a Summoner before and after the reset of a season, with the final rank. */
@Entity
@Table(name = "season_result")
@Getter
@Setter
@NoArgsConstructor
public class SeasonResult {

	@EmbeddedId
	private SeasonResultId id = new SeasonResultId();

	@MapsId("seasonId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "season_id")
	private Season season;

	@MapsId("playerId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id")
	private Player player;

	@Column(name = "trophies_before", nullable = false)
	private int trophiesBefore;

	@Column(name = "trophies_after", nullable = false)
	private int trophiesAfter;

	@Column(name = "final_rank", nullable = false)
	private int finalRank;
}
