package com.horizonglitch.wotv.pack;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import com.horizonglitch.wotv.player.Player;

/** A pack opened by a Summoner, with the Shards paid. */
@Entity
@Table(name = "pack_opening")
@Getter
@Setter
@NoArgsConstructor
public class PackOpening {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id", nullable = false)
	private Player player;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pack_type_id", nullable = false)
	private PackType packType;

	@Column(name = "shards_spent", nullable = false)
	private int shardsSpent;

	/** Empty when no guarantee applied. */
	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private GuaranteeType guarantee;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@OneToMany(mappedBy = "packOpening", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<PackOpeningCard> cards = new ArrayList<>();
}
