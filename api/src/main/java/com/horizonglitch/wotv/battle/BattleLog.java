package com.horizonglitch.wotv.battle;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Turn-by-turn log of a PLAYED battle, kept apart so the history list does not load it. */
@Entity
@Table(name = "battle_log")
@Getter
@Setter
@NoArgsConstructor
public class BattleLog {

	@Id
	@Column(name = "battle_id")
	private Long battleId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "battle_id")
	private Battle battle;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "turn_log", nullable = false)
	private String turnLog;
}
