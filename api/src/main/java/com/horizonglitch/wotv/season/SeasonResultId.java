package com.horizonglitch.wotv.season;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Primary key of {@link SeasonResult}. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SeasonResultId implements Serializable {

	@Column(name = "season_id")
	private Integer seasonId;

	@Column(name = "player_id")
	private Long playerId;
}
