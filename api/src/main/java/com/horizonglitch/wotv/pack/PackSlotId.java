package com.horizonglitch.wotv.pack;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Primary key of {@link PackSlot}. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PackSlotId implements Serializable {

	@Column(name = "pack_type_id")
	private Integer packTypeId;

	@Column(name = "position")
	private Short position;
}
