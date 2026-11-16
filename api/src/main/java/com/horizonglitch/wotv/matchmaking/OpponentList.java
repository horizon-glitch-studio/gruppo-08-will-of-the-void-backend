package com.horizonglitch.wotv.matchmaking;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.horizonglitch.wotv.player.Player;

/** The saved list of 5 opponents of a Summoner for one mode (QUICK or RANKED). */
@Entity
@Table(name = "opponent_list")
@Getter
@Setter
@NoArgsConstructor
public class OpponentList {

	@EmbeddedId
	private OpponentListId id = new OpponentListId();

	@MapsId("playerId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id")
	private Player player;

	@Column(name = "generated_at", nullable = false)
	private Instant generatedAt;

	/** Start of the 5-second refresh cooldown. */
	@Column(name = "last_refresh_at")
	private Instant lastRefreshAt;

	@OneToMany(mappedBy = "list", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id.position")
	private List<OpponentListEntry> entries = new ArrayList<>();
}
