package com.horizonglitch.wotv.shard;

/** Shards given at login; 0 means the bonus was not due. */
public record LoginBonuses(int daily, int comeback) {

	public static LoginBonuses none() {
		return new LoginBonuses(0, 0);
	}
}
