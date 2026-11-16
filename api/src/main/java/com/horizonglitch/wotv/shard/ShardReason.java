package com.horizonglitch.wotv.shard;

/** Origin of a {@link ShardTransaction}. Stored as text; the values match the CHECK constraint of the column. */
public enum ShardReason {
	REGISTRATION_BONUS, DAILY_BONUS, COMEBACK_BONUS, SEASON_REWARD, ORBIT_REWARD, INVITE_BONUS, PACK_PURCHASE
}
