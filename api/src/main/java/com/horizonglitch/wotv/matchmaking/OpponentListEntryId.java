package com.horizonglitch.wotv.matchmaking;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.horizonglitch.wotv.battle.BattleMode;

/** Primary key of {@link OpponentListEntry}. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class OpponentListEntryId implements Serializable {

	@Column(name = "player_id")
	private Long playerId;

	@Enumerated(EnumType.STRING)
	@Column(name = "mode", length = 8)
	private BattleMode mode;

	@Column(name = "position")
	private Short position;
}
