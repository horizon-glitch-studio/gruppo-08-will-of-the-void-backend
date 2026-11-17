package com.horizonglitch.wotv.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.horizonglitch.wotv.engine.MatchEvent.BattleFinished;
import com.horizonglitch.wotv.engine.MatchEvent.CardDeployed;
import com.horizonglitch.wotv.engine.MatchEvent.ClashCardOutcome;
import com.horizonglitch.wotv.engine.MatchEvent.ClashOutcome;
import com.horizonglitch.wotv.engine.MatchEvent.ClashResolved;
import com.horizonglitch.wotv.engine.MatchEvent.DeckExhausted;
import com.horizonglitch.wotv.engine.MatchEvent.EnergyAward;
import com.horizonglitch.wotv.engine.MatchEvent.ExhaustedSide;
import com.horizonglitch.wotv.engine.MatchEvent.RoundCardState;
import com.horizonglitch.wotv.engine.MatchEvent.RoundResolved;

/**
 * Simulates a match from start to end, following rules/match.md and rules/factions.md. The Summoners make no
 * choices: the engine shuffles the decks with the seed and plays every clash. Stateless and deterministic: the
 * same request always gives the same result (MATCH-02, MATCH-18).
 */
public final class MatchEngine {

	public MatchResult play(MatchRequest request) {
		Random random = new Random(request.seed());
		List<CardSnapshot> deckA = shuffle(request.deckA(), random);
		List<CardSnapshot> deckB = shuffle(request.deckB(), random);
		return simulate(deckA, deckB, request.parameters().winThreshold(request.mode()), request.parameters());
	}

	/**
	 * Shuffles with {@link Collections#shuffle(List, Random)} and {@link Random}: both algorithms are specified
	 * by their Javadoc, so the same seed gives the same order on every JDK. Deck A is shuffled first, deck B
	 * second, with the same generator.
	 */
	private static List<CardSnapshot> shuffle(List<CardInput> deck, Random random) {
		List<CardInput> order = new ArrayList<>(deck);
		Collections.shuffle(order, random);
		List<CardSnapshot> snapshots = new ArrayList<>(order.size());
		for (int i = 0; i < order.size(); i++) {
			snapshots.add(CardSnapshot.of(i, order.get(i)));
		}
		return List.copyOf(snapshots);
	}

	/** Plays the decks in the given order, without shuffling. Package-private for tests that fix the draw order. */
	MatchResult simulate(List<CardSnapshot> deckA, List<CardSnapshot> deckB, int winThreshold,
			MatchParameters parameters) {
		Simulation simulation = new Simulation(deckA, deckB, winThreshold, parameters);
		return simulation.run();
	}

	/** A creature in the Arena: its card and the DEF it has left (MATCH-05, MATCH-10). */
	private static final class Creature {
		final CardSnapshot card;
		int defense;
		int effectiveAttack;
		boolean advantage;

		Creature(CardSnapshot card) {
			this.card = card;
			this.defense = card.baseDefense();
		}

		boolean defeated() {
			return defense <= 0;
		}
	}

	private static final class Simulation {
		private final Map<Side, List<CardSnapshot>> decks = new EnumMap<>(Side.class);
		private final Map<Side, Integer> nextCard = new EnumMap<>(Side.class);
		private final Map<Side, Creature> arena = new EnumMap<>(Side.class);
		private final Map<Side, Integer> energy = new EnumMap<>(Side.class);
		private final int winThreshold;
		private final MatchParameters parameters;
		private final List<MatchEvent> events = new ArrayList<>();
		private int clashNumber;

		Simulation(List<CardSnapshot> deckA, List<CardSnapshot> deckB, int winThreshold, MatchParameters parameters) {
			decks.put(Side.A, deckA);
			decks.put(Side.B, deckB);
			for (Side side : Side.values()) {
				nextCard.put(side, 0);
				energy.put(side, 0); // MATCH-01
			}
			this.winThreshold = winThreshold;
			this.parameters = parameters;
		}

		MatchResult run() {
			while (true) {
				clashNumber++;
				ExhaustedSide exhausted = exhaustedSide();
				if (exhausted != null) {
					return finishByExhaustion(exhausted);
				}
				deployMissing();
				playClash();
				Side winner = thresholdReached();
				if (winner != null) {
					return finish(EndReason.ENERGY_THRESHOLD, Winner.of(winner));
				}
			}
		}

		/** MATCH-13: a side that has to deploy and has no card left. Null when every side can deploy. */
		private ExhaustedSide exhaustedSide() {
			boolean aOut = arena.get(Side.A) == null && undeployed(Side.A) == 0;
			boolean bOut = arena.get(Side.B) == null && undeployed(Side.B) == 0;
			if (aOut && bOut) {
				return ExhaustedSide.BOTH;
			}
			if (aOut) {
				return ExhaustedSide.A;
			}
			if (bOut) {
				return ExhaustedSide.B;
			}
			return null;
		}

		private int undeployed(Side side) {
			return decks.get(side).size() - nextCard.get(side);
		}

		/** MATCH-03 and MATCH-11: every side without a creature in the Arena deploys its next card, A first. */
		private void deployMissing() {
			for (Side side : Side.values()) {
				if (arena.get(side) == null) {
					int position = nextCard.get(side);
					CardSnapshot card = decks.get(side).get(position);
					nextCard.put(side, position + 1);
					Creature creature = new Creature(card);
					arena.put(side, creature);
					events.add(new CardDeployed(nextSequence(), clashNumber, side, card.deckPosition(), creature.defense));
				}
			}
		}

		private void playClash() {
			Creature a = arena.get(Side.A);
			Creature b = arena.get(Side.B);
			setEffectiveAttack(a, b);
			setEffectiveAttack(b, a);

			int round = 0;
			do {
				round++;
				int defenseBeforeA = a.defense;
				int defenseBeforeB = b.defense;
				a.defense -= b.effectiveAttack; // MATCH-04: both hit at the same time
				b.defense -= a.effectiveAttack;
				events.add(new RoundResolved(nextSequence(), clashNumber, round, List.of(
						roundState(Side.A, a, defenseBeforeA, b.effectiveAttack),
						roundState(Side.B, b, defenseBeforeB, a.effectiveAttack))));
			} while (!a.defeated() && !b.defeated()); // MATCH-07

			resolveClash(a, b);
		}

		/** FAC-02 and FAC-06: the effective ATK is set when the two creatures meet and lasts the whole clash. */
		private void setEffectiveAttack(Creature creature, Creature opposing) {
			int bonus = parameters.advantages().bonus(creature.card.faction(), opposing.card.faction());
			creature.advantage = bonus > 0;
			creature.effectiveAttack = creature.card.baseAttack() + bonus;
		}

		private static RoundCardState roundState(Side side, Creature creature, int defenseBefore, int damageTaken) {
			return new RoundCardState(side, creature.card.deckPosition(), creature.effectiveAttack, creature.advantage,
					defenseBefore, damageTaken, creature.defense, creature.defeated());
		}

		private void resolveClash(Creature a, Creature b) {
			ClashOutcome outcome;
			EnergyAward award = null;
			if (a.defeated() && b.defeated()) {
				outcome = ClashOutcome.BOTH_DEFEATED; // MATCH-09: nobody gains Energy
			} else if (a.defeated()) {
				outcome = ClashOutcome.A_DEFEATED;
				award = award(Side.B, Side.A, a);
			} else {
				outcome = ClashOutcome.B_DEFEATED;
				award = award(Side.A, Side.B, b);
			}
			if (award != null) {
				energy.merge(award.recipientSide(), award.amount(), Integer::sum);
			}
			events.add(new ClashResolved(nextSequence(), clashNumber, outcome, List.of(
					new ClashCardOutcome(Side.A, a.card.deckPosition(), a.defense, a.defeated()),
					new ClashCardOutcome(Side.B, b.card.deckPosition(), b.defense, b.defeated())),
					award, energyBySide()));
			if (a.defeated()) {
				arena.remove(Side.A);
			}
			if (b.defeated()) {
				arena.remove(Side.B);
			}
		}

		/** MATCH-08: the Energy is the excess damage, the DEF below 0 of the defeated creature. */
		private static EnergyAward award(Side recipient, Side defeated, Creature defeatedCreature) {
			return new EnergyAward(recipient, defeated, -defeatedCreature.defense);
		}

		/** MATCH-12: only one side gains Energy per clash, so at most one side can reach the threshold. */
		private Side thresholdReached() {
			for (Side side : Side.values()) {
				if (energy.get(side) >= winThreshold) {
					return side;
				}
			}
			return null;
		}

		/** MATCH-14 to MATCH-16: the end bonus decides only who has more Energy. */
		private MatchResult finishByExhaustion(ExhaustedSide exhausted) {
			int bonusPerCard = parameters.endBonusPerCard();
			EnergyBySide remaining = new EnergyBySide(remainingCards(Side.A), remainingCards(Side.B));
			EnergyBySide bonus = new EnergyBySide(remaining.A() * bonusPerCard, remaining.B() * bonusPerCard);
			for (Side side : Side.values()) {
				energy.merge(side, bonus.of(side), Integer::sum);
			}
			events.add(new DeckExhausted(nextSequence(), exhausted, remaining, bonusPerCard, bonus, energyBySide()));

			int a = energy.get(Side.A);
			int b = energy.get(Side.B);
			Winner winner = a == b ? Winner.DRAW : a > b ? Winner.A : Winner.B;
			return finish(EndReason.DECK_EXHAUSTED, winner);
		}

		/** The cards a Summoner still has: the undeployed ones plus the creature in the Arena, if any. */
		private int remainingCards(Side side) {
			return undeployed(side) + (arena.get(side) == null ? 0 : 1);
		}

		private MatchResult finish(EndReason reason, Winner winner) {
			EnergyBySide finalEnergy = energyBySide();
			events.add(new BattleFinished(nextSequence(), reason, winner, finalEnergy));
			return new MatchResult(winner, reason, finalEnergy, decks.get(Side.A), decks.get(Side.B),
					List.copyOf(events));
		}

		private EnergyBySide energyBySide() {
			return new EnergyBySide(energy.get(Side.A), energy.get(Side.B));
		}

		private int nextSequence() {
			return events.size() + 1;
		}
	}
}
