package com.horizonglitch.wotv.engine;

/** The two sides of a match. Side A is the challenger, side B the opponent. */
public enum Side {
	A, B;

	public Side opponent() {
		return this == A ? B : A;
	}
}
