package com.horizonglitch.wotv.engine;

/** Who won the match, or DRAW when both Summoners end with the same Energy (MATCH-15). */
public enum Winner {
	A, B, DRAW;

	public static Winner of(Side side) {
		return side == Side.A ? A : B;
	}
}
