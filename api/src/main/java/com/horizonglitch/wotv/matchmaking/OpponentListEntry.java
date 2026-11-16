package com.horizonglitch.wotv.matchmaking;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.horizonglitch.wotv.battle.Battle;
import com.horizonglitch.wotv.player.Player;

/** An opponent in a list, at position 1 to 5, and the battle played against them.
 *
 * <p>The list is a read-only association over the key columns: set the key with {@link #OpponentListEntry(OpponentList, short)}. */
@Entity
@Table(name = "opponent_list_entry")
@Getter
@Setter
@NoArgsConstructor
public class OpponentListEntry {

	@EmbeddedId
	private OpponentListEntryId id = new OpponentListEntryId();

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumns({
		@JoinColumn(name = "player_id", referencedColumnName = "player_id", insertable = false, updatable = false),
		@JoinColumn(name = "mode", referencedColumnName = "mode", insertable = false, updatable = false)
	})
	private OpponentList list;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "opponent_id", nullable = false)
	private Player opponent;

	/** For display only: trophy changes always use the current values. */
	@Column(name = "trophies_at_generation", nullable = false)
	private int trophiesAtGeneration;

	/** Set only when the battle is PLAYED. */
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "battle_id", unique = true)
	private Battle battle;

	public OpponentListEntry(OpponentList list, short position) {
		this.id = new OpponentListEntryId(list.getId().getPlayerId(), list.getId().getMode(), position);
		this.list = list;
	}
}
