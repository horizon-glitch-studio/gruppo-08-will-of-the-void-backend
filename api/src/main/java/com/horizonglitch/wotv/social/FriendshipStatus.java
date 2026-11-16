package com.horizonglitch.wotv.social;

/** State of a {@link Friendship}: rejecting or removing deletes the row. Stored as text; the values match the CHECK constraint of the column. */
public enum FriendshipStatus {
	PENDING, ACCEPTED
}
