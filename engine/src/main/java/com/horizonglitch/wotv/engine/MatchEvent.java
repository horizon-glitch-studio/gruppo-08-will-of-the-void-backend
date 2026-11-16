package com.horizonglitch.wotv.engine;

import java.util.List;

/**
 * One event of the match log. Every record has exactly the fields of the matching event in
 * battle-viewer.schema.json, with the same names and order, so the API can serialize the list as
 * {@code events} without mapping. {@code type} is the discriminator required by the schema.
 */
public sealed interface MatchEvent {

	int sequence();

	String type();

	/** A Summoner deploys a card in the Arena (MATCH-03, MATCH-11). */
	record CardDeployed(int sequence, String type, int clashNumber, Side side, int deckPosition,
			int defenseAtDeployment) implements MatchEvent {

		public static final String TYPE = "CARD_DEPLOYED";

		public CardDeployed(int sequence, int clashNumber, Side side, int deckPosition, int defenseAtDeployment) {
			this(sequence, TYPE, clashNumber, side, deckPosition, defenseAtDeployment);
		}
	}

	/** The state of one card after a round, as {@code roundCardState} in the schema. */
	record RoundCardState(Side side, int deckPosition, int attackUsed, boolean advantageApplied,
			int defenseBefore, int damageTaken, int defenseAfter, boolean defeated) {
	}

	/** Both creatures hit at the same time (MATCH-04). {@code cards} holds side A first, then side B. */
	record RoundResolved(int sequence, String type, int clashNumber, int roundNumber,
			List<RoundCardState> cards) implements MatchEvent {

		public static final String TYPE = "ROUND_RESOLVED";

		public RoundResolved(int sequence, int clashNumber, int roundNumber, List<RoundCardState> cards) {
			this(sequence, TYPE, clashNumber, roundNumber, List.copyOf(cards));
		}
	}

	/** The final state of one card at the end of a clash, as {@code clashCardOutcome} in the schema. */
	record ClashCardOutcome(Side side, int deckPosition, int finalDefense, boolean defeated) {
	}

	/** The Energy a surviving creature gives its Summoner (MATCH-08); zero when the loser ended at exactly 0 DEF. */
	record EnergyAward(Side recipientSide, Side defeatedSide, int amount) {
	}

	/** Outcome of a clash: which side's card was defeated. */
	enum ClashOutcome {
		BOTH_DEFEATED, A_DEFEATED, B_DEFEATED
	}

	/**
	 * A clash ended because at least one creature was defeated (MATCH-07). {@code energyAward} is null when
	 * both were defeated (MATCH-09).
	 */
	record ClashResolved(int sequence, String type, int clashNumber, ClashOutcome outcome,
			List<ClashCardOutcome> cards, EnergyAward energyAward, EnergyBySide energyAfter) implements MatchEvent {

		public static final String TYPE = "CLASH_RESOLVED";

		public ClashResolved(int sequence, int clashNumber, ClashOutcome outcome, List<ClashCardOutcome> cards,
				EnergyAward energyAward, EnergyBySide energyAfter) {
			this(sequence, TYPE, clashNumber, outcome, List.copyOf(cards), energyAward, energyAfter);
		}
	}

	/** Which side had to deploy a card and had none left. */
	enum ExhaustedSide {
		A, B, BOTH
	}

	/** A Summoner has to deploy a card and has none left (MATCH-13); the end bonus is applied (MATCH-14). */
	record DeckExhausted(int sequence, String type, ExhaustedSide exhaustedSide, EnergyBySide remainingCardsBySide,
			int bonusPerCard, EnergyBySide bonusAwardedBySide, EnergyBySide energyAfter) implements MatchEvent {

		public static final String TYPE = "DECK_EXHAUSTED";

		public DeckExhausted(int sequence, ExhaustedSide exhaustedSide, EnergyBySide remainingCardsBySide,
				int bonusPerCard, EnergyBySide bonusAwardedBySide, EnergyBySide energyAfter) {
			this(sequence, TYPE, exhaustedSide, remainingCardsBySide, bonusPerCard, bonusAwardedBySide, energyAfter);
		}
	}

	/** The last event of every match. */
	record BattleFinished(int sequence, String type, EndReason endReason, Winner winnerSide,
			EnergyBySide finalEnergyBySide) implements MatchEvent {

		public static final String TYPE = "BATTLE_FINISHED";

		public BattleFinished(int sequence, EndReason endReason, Winner winnerSide, EnergyBySide finalEnergyBySide) {
			this(sequence, TYPE, endReason, winnerSide, finalEnergyBySide);
		}
	}
}
