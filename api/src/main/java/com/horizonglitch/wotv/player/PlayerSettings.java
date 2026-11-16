package com.horizonglitch.wotv.player;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import org.hibernate.annotations.UpdateTimestamp;

/** Accessibility settings, one row per Summoner, created at registration. */
@Entity
@Table(name = "player_settings")
@Getter
@Setter
@NoArgsConstructor
public class PlayerSettings {

	@Id
	@Column(name = "player_id")
	private Long playerId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id")
	private Player player;

	@Column(name = "reduce_motion", nullable = false)
	private boolean reduceMotion;

	@Column(name = "reduce_transparency", nullable = false)
	private boolean reduceTransparency;

	/** Writes faction, rarity and advantages as text next to their colour or icon. */
	@Column(name = "show_text_labels", nullable = false)
	private boolean showTextLabels;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
