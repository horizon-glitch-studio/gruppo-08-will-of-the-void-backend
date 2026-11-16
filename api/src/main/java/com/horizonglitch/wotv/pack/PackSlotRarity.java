package com.horizonglitch.wotv.pack;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import com.horizonglitch.wotv.card.Rarity;

/** A rarity a slot can give, with its probability in percent. The probabilities of a slot add up to 100.
 *
 * <p>The slot is a read-only association over the key columns: set the key with {@link #PackSlotRarity(PackSlot, Rarity, BigDecimal)}. */
@Entity
@Table(name = "pack_slot_rarity")
@Getter
@Setter
@NoArgsConstructor
public class PackSlotRarity {

	@EmbeddedId
	private PackSlotRarityId id = new PackSlotRarityId();

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumns({
		@JoinColumn(name = "pack_type_id", referencedColumnName = "pack_type_id", insertable = false, updatable = false),
		@JoinColumn(name = "position", referencedColumnName = "position", insertable = false, updatable = false)
	})
	private PackSlot slot;

	@MapsId("rarityId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "rarity_id")
	private Rarity rarity;

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal probability;

	public PackSlotRarity(PackSlot slot, Rarity rarity, BigDecimal probability) {
		this.id = new PackSlotRarityId(slot.getId().getPackTypeId(), slot.getId().getPosition(), rarity.getId());
		this.slot = slot;
		this.rarity = rarity;
		this.probability = probability;
	}
}
