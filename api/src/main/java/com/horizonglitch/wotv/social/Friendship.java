package com.horizonglitch.wotv.social;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import com.horizonglitch.wotv.player.Player;

/** A friend request or an accepted friendship. */
@Entity
@Table(name = "friendship")
@Getter
@Setter
@NoArgsConstructor
public class Friendship {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "requester_id", nullable = false)
	private Player requester;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "addressee_id", nullable = false)
	private Player addressee;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private FriendshipStatus status = FriendshipStatus.PENDING;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Set exactly when the status is ACCEPTED. */
	@Column(name = "accepted_at")
	private Instant acceptedAt;
}
