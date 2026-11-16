package com.horizonglitch.wotv.pack;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

/** A slot of a pack: it gives one card. */
@Entity
@Table(name = "pack_slot")
@Getter
@Setter
@NoArgsConstructor
public class PackSlot {

	@EmbeddedId
	private PackSlotId id = new PackSlotId();

	@MapsId("packTypeId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pack_type_id")
	private PackType packType;

	@OneToMany(mappedBy = "slot", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<PackSlotRarity> rarities = new ArrayList<>();
}
