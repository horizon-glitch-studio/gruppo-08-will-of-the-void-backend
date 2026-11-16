package com.horizonglitch.wotv.player;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An avatar a Summoner can choose. The image is a static file of the backend. */
@Entity
@Table(name = "avatar")
@Getter
@Setter
@NoArgsConstructor
public class Avatar {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Short id;

	@Column(nullable = false, unique = true, length = 50)
	private String name;

	/** Relative path of the static file, for example {@code /avatars/03.webp}. */
	@Column(name = "image_url", nullable = false)
	private String imageUrl;

	@Column(name = "sort_order", nullable = false, unique = true)
	private short sortOrder;

	/** An avatar is never deleted: inactive avatars are hidden from the choice. */
	@Column(name = "is_active", nullable = false)
	private boolean active = true;
}
