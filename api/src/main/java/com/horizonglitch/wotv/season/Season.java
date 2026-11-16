package com.horizonglitch.wotv.season;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

/** A 4-week Ranked season. */
@Entity
@Table(name = "season")
@Getter
@Setter
@NoArgsConstructor
public class Season {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "starts_at", nullable = false, unique = true)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	/** Set when the end-of-season reset has run, so it never runs twice. */
	@Column(name = "reset_at")
	private Instant resetAt;
}
