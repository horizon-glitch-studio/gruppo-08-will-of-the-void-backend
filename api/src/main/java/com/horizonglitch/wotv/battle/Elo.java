package com.horizonglitch.wotv.battle;

/** The Elo rule of rules/ranking.md (RANK-03, RANK-04), as a pure function. */
public final class Elo {

	public static final double WIN = 1.0;
	public static final double DRAW = 0.5;
	public static final double DEFEAT = 0.0;

	private Elo() {
	}

	/**
	 * The change of trophies of A after a match against B: {@code k} × (result − expected result), rounded to the
	 * nearest whole number. B changes by the same amount with the opposite sign; the floor at 0 (RANK-05) is
	 * applied by the caller.
	 */
	public static int delta(int trophiesA, int trophiesB, double resultA, int k) {
		double expected = 1.0 / (1.0 + Math.pow(10, (trophiesB - trophiesA) / 400.0));
		return (int) Math.round(k * (resultA - expected));
	}
}
