package com.horizonglitch.wotv.engine;

/**
 * The parameters of rules/parameters.md that the match uses. The engine holds no value of its own: the API
 * passes them, so the ones marked [TEST] can change without touching the engine.
 *
 * @param winThresholdFriend WIN_THRESHOLD_FRIEND (MATCH-12)
 * @param winThresholdRanked WIN_THRESHOLD_RANKED (MATCH-12)
 * @param winThresholdQuick WIN_THRESHOLD_QUICK (MATCH-12)
 * @param endBonusPerCard END_BONUS_PER_CARD (MATCH-14)
 * @param advantages the faction advantages, ADVANTAGE_VALUE included (FAC-02)
 */
public record MatchParameters(int winThresholdFriend, int winThresholdRanked, int winThresholdQuick,
		int endBonusPerCard, AdvantageTable advantages) {

	public MatchParameters {
		if (winThresholdFriend < 1 || winThresholdRanked < 1 || winThresholdQuick < 1) {
			throw new IllegalArgumentException("Win thresholds must be at least 1");
		}
		if (endBonusPerCard < 0) {
			throw new IllegalArgumentException("END_BONUS_PER_CARD cannot be negative");
		}
		if (advantages == null) {
			throw new IllegalArgumentException("advantages are required");
		}
	}

	public int winThreshold(MatchMode mode) {
		return switch (mode) {
			case FRIEND -> winThresholdFriend;
			case RANKED -> winThresholdRanked;
			case QUICK -> winThresholdQuick;
		};
	}
}
