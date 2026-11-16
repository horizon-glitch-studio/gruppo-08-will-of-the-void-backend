package com.horizonglitch.wotv.pack;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A type of pack. EVENT packs have a purchase window, STANDARD packs do not. */
@Entity
@Table(name = "pack_type")
@Getter
@Setter
@NoArgsConstructor
public class PackType {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 8)
	private PackKind kind = PackKind.STANDARD;

	@Column(name = "shard_cost", nullable = false)
	private int shardCost;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "available_from")
	private Instant availableFrom;

	@Column(name = "available_until")
	private Instant availableUntil;

	@OneToMany(mappedBy = "packType", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id.position")
	private List<PackSlot> slots = new ArrayList<>();
}
