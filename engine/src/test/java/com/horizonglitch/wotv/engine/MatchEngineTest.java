package com.horizonglitch.wotv.engine;

import static com.horizonglitch.wotv.engine.Decks.OFFICIAL;
import static com.horizonglitch.wotv.engine.Decks.input;
import static com.horizonglitch.wotv.engine.Decks.ordered;
import static com.horizonglitch.wotv.engine.Decks.repeated;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

import com.horizonglitch.wotv.engine.MatchEvent.BattleFinished;
import com.horizonglitch.wotv.engine.MatchEvent.CardDeployed;
import com.horizonglitch.wotv.engine.MatchEvent.ClashOutcome;
import com.horizonglitch.wotv.engine.MatchEvent.ClashResolved;
import com.horizonglitch.wotv.engine.MatchEvent.DeckExhausted;
import com.horizonglitch.wotv.engine.MatchEvent.ExhaustedSide;
import com.horizonglitch.wotv.engine.MatchEvent.RoundResolved;

/** One test for every rule of rules/match.md and rules/factions.md, with the draw order fixed. */
class MatchEngineTest {

	private final MatchEngine engine = new MatchEngine();

	private MatchResult play(List<CardSnapshot> deckA, List<CardSnapshot> deckB) {
		return engine.simulate(deckA, deckB, OFFICIAL.winThresholdRanked(), OFFICIAL);
	}

	private static List<RoundResolved> rounds(MatchResult result) {
		return result.events().stream().filter(RoundResolved.class::isInstance).map(RoundResolved.class::cast).toList();
	}

	private static List<ClashResolved> clashes(MatchResult result) {
		return result.events().stream().filter(ClashResolved.class::isInstance).map(ClashResolved.class::cast).toList();
	}

	private static List<CardDeployed> deployments(MatchResult result) {
		return result.events().stream().filter(CardDeployed.class::isInstance).map(CardDeployed.class::cast).toList();
	}

	// --- Factions

	@Test
	void fac02_prevailingCreatureGetsTheAdvantageOnAttackOnly() {
		MatchResult result = play(ordered(input("Lava", 5, 10)), ordered(input("Metal", 5, 10)));

		RoundResolved first = rounds(result).getFirst();
		assertThat(first.cards().get(0).attackUsed()).isEqualTo(8);
		assertThat(first.cards().get(0).advantageApplied()).isTrue();
		assertThat(first.cards().get(1).attackUsed()).isEqualTo(5);
		assertThat(first.cards().get(1).advantageApplied()).isFalse();
		// FAC-07: DEF is not changed by the advantage
		assertThat(first.cards().get(0).defenseBefore()).isEqualTo(10);
		assertThat(first.cards().get(1).defenseBefore()).isEqualTo(10);
	}

	@Test
	void fac03_advantageNeverLowersTheOpponentAttack() {
		MatchResult result = play(ordered(input("Lava", 5, 10)), ordered(input("Metal", 5, 10)));

		assertThat(rounds(result).getFirst().cards().get(0).damageTaken()).isEqualTo(5);
	}

	@Test
	void fac04_evenAndSameFactionsClashWithNoModifier() {
		MatchResult even = play(ordered(input("Lava", 5, 10)), ordered(input("Ice", 5, 10)));
		MatchResult same = play(ordered(input("Lava", 5, 10)), ordered(input("Lava", 5, 10)));

		for (MatchResult result : List.of(even, same)) {
			RoundResolved first = rounds(result).getFirst();
			assertThat(first.cards()).allSatisfy(c -> {
				assertThat(c.attackUsed()).isEqualTo(5);
				assertThat(c.advantageApplied()).isFalse();
			});
		}
	}

	@Test
	void fac05_inertFactionsNeverGiveOrReceiveAnAdvantage() {
		MatchResult gemVsLava = play(ordered(input("Gem", 5, 10)), ordered(input("Lava", 5, 10)));
		MatchResult lavaVsNobleGas = play(ordered(input("Lava", 5, 10)), ordered(input("Noble Gas", 5, 10)));
		MatchResult gemVsNobleGas = play(ordered(input("Gem", 5, 10)), ordered(input("Noble Gas", 5, 10)));

		for (MatchResult result : List.of(gemVsLava, lavaVsNobleGas, gemVsNobleGas)) {
			assertThat(rounds(result).getFirst().cards()).allSatisfy(c -> {
				assertThat(c.attackUsed()).isEqualTo(5);
				assertThat(c.advantageApplied()).isFalse();
			});
		}
	}

	@Test
	void fac06_effectiveAttackIsSetAgainAgainstEveryNewOpponent() {
		// Lava prevails over Metal, then meets Electro, which prevails over Lava
		MatchResult result = play(ordered(input("Lava", 5, 30)), ordered(input("Metal", 1, 8), input("Electro", 1, 8)));

		List<RoundResolved> rounds = rounds(result);
		assertThat(rounds.get(0).clashNumber()).isEqualTo(1);
		assertThat(rounds.get(0).cards().get(0).attackUsed()).isEqualTo(8);
		assertThat(rounds.get(1).clashNumber()).isEqualTo(2);
		assertThat(rounds.get(1).cards().get(0).attackUsed()).isEqualTo(5);
		assertThat(rounds.get(1).cards().get(0).advantageApplied()).isFalse();
		assertThat(rounds.get(1).cards().get(1).attackUsed()).isEqualTo(4);
		assertThat(rounds.get(1).cards().get(1).advantageApplied()).isTrue();
	}

	@Test
	void fac01_everyElementalFactionPrevailsOverTwoAndIsOverpoweredByTwo() {
		AdvantageTable table = AdvantageTable.official(3);
		List<String> elemental = List.of("Lava", "Electro", "Radio", "Ice", "Metal", "Matte");
		for (String faction : elemental) {
			long prevails = elemental.stream().filter(o -> table.bonus(faction, o) > 0).count();
			long overpowered = elemental.stream().filter(o -> table.bonus(o, faction) > 0).count();
			assertThat(prevails).as("%s prevails over", faction).isEqualTo(2);
			assertThat(overpowered).as("%s is overpowered by", faction).isEqualTo(2);
			assertThat(table.bonus(faction, faction)).isZero();
		}
	}

	@Test
	void advantageValueAndTableComeFromTheParameters() {
		AdvantageTable custom = AdvantageTable.of(List.of(new AdvantageTable.Advantage("Ice", "Lava", 7)));
		MatchParameters parameters = new MatchParameters(30, 30, 20, 4, custom);

		MatchResult result = engine.simulate(ordered(input("Ice", 2, 10)), ordered(input("Lava", 2, 10)), 30, parameters);

		assertThat(rounds(result).getFirst().cards().get(0).attackUsed()).isEqualTo(9);
		assertThat(rounds(result).getFirst().cards().get(1).attackUsed()).isEqualTo(2);
	}

	// --- Match

	@Test
	void match01_bothSummonersStartWithZeroEnergy() {
		MatchResult result = play(ordered(input("Lava", 5, 5)), ordered(input("Lava", 5, 5)));

		assertThat(clashes(result).getFirst().energyAfter()).isEqualTo(new EnergyBySide(0, 0));
	}

	@Test
	void match03_bothSummonersDeployTheirFirstCardAtTheStart() {
		MatchResult result = play(ordered(input("Lava", 5, 5)), ordered(input("Lava", 5, 5)));

		assertThat(result.events().get(0)).isEqualTo(new CardDeployed(1, 1, Side.A, 0, 5));
		assertThat(result.events().get(1)).isEqualTo(new CardDeployed(2, 1, Side.B, 0, 5));
	}

	@Test
	void match04to07_creaturesHitAtTheSameTimeEveryRoundUntilOneIsDefeated() {
		MatchResult result = play(ordered(input("Lava", 3, 10)), ordered(input("Lava", 4, 7)));

		List<RoundResolved> rounds = rounds(result);
		assertThat(rounds).hasSize(3);
		assertThat(rounds.get(0).cards().get(0).defenseAfter()).isEqualTo(6);
		assertThat(rounds.get(0).cards().get(1).defenseAfter()).isEqualTo(4);
		assertThat(rounds.get(1).cards().get(0).defenseAfter()).isEqualTo(2);
		assertThat(rounds.get(1).cards().get(1).defenseAfter()).isEqualTo(1);
		assertThat(rounds.get(2).cards().get(0).defenseAfter()).isEqualTo(-2);
		assertThat(rounds.get(2).cards().get(0).defeated()).isTrue();
		assertThat(rounds.get(2).cards().get(1).defenseAfter()).isEqualTo(-2);
		assertThat(rounds.get(2).cards().get(1).defeated()).isTrue();
		assertThat(rounds).allSatisfy(r -> assertThat(r.cards()).allSatisfy(c -> assertThat(c.attackUsed())
				.as("MATCH-05: ATK never changes").isEqualTo(c.side() == Side.A ? 3 : 4)));
	}

	@Test
	void match08_survivorGainsEnergyEqualToTheExcessDamage() {
		MatchResult result = play(ordered(input("Lava", 9, 10)), ordered(input("Lava", 1, 5)));

		ClashResolved clash = clashes(result).getFirst();
		assertThat(clash.outcome()).isEqualTo(ClashOutcome.B_DEFEATED);
		assertThat(clash.energyAward()).isEqualTo(new MatchEvent.EnergyAward(Side.A, Side.B, 4));
		assertThat(clash.energyAfter()).isEqualTo(new EnergyBySide(4, 0));
	}

	@Test
	void match08_creatureDefeatedAtExactlyZeroGivesNoEnergy() {
		MatchResult result = play(ordered(input("Lava", 5, 10)), ordered(input("Lava", 1, 5)));

		ClashResolved clash = clashes(result).getFirst();
		assertThat(clash.outcome()).isEqualTo(ClashOutcome.B_DEFEATED);
		assertThat(clash.energyAward()).isEqualTo(new MatchEvent.EnergyAward(Side.A, Side.B, 0));
		assertThat(clash.energyAfter()).isEqualTo(new EnergyBySide(0, 0));
	}

	@Test
	void match09and11_bothDefeatedGivesNoEnergyAndBothDeployTheNextCard() {
		MatchResult result = play(ordered(input("Lava", 9, 5), input("Lava", 1, 1)),
				ordered(input("Lava", 9, 5), input("Lava", 1, 1)));

		ClashResolved first = clashes(result).getFirst();
		assertThat(first.outcome()).isEqualTo(ClashOutcome.BOTH_DEFEATED);
		assertThat(first.energyAward()).isNull();
		assertThat(first.energyAfter()).isEqualTo(new EnergyBySide(0, 0));
		assertThat(deployments(result)).containsExactly(
				new CardDeployed(1, 1, Side.A, 0, 5), new CardDeployed(2, 1, Side.B, 0, 5),
				new CardDeployed(5, 2, Side.A, 1, 1), new CardDeployed(6, 2, Side.B, 1, 1));
	}

	@Test
	void match10_survivingCreatureStaysInTheArenaWithTheDefenseItHasLeft() {
		MatchResult result = play(ordered(input("Lava", 8, 8)), ordered(input("Lava", 5, 7), input("Lava", 6, 6)));

		List<ClashResolved> clashes = clashes(result);
		assertThat(clashes.get(0).cards().get(0).finalDefense()).isEqualTo(3);
		RoundResolved secondClash = rounds(result).get(1);
		assertThat(secondClash.cards().get(0).deckPosition()).isZero();
		assertThat(secondClash.cards().get(0).defenseBefore()).isEqualTo(3);
		assertThat(deployments(result)).extracting(CardDeployed::side).containsExactly(Side.A, Side.B, Side.B);
	}

	@Test
	void match11_onlyTheSummonerWhoseCreatureWasDefeatedDeploys() {
		MatchResult result = play(ordered(input("Lava", 8, 8)), ordered(input("Lava", 5, 7), input("Lava", 6, 6)));

		assertThat(deployments(result).get(2)).isEqualTo(new CardDeployed(5, 2, Side.B, 1, 6));
	}

	@Test
	void match12_summonerWhoReachesTheThresholdWinsAfterTheClash() {
		// every clash gives A 10 Energy: 10, 20, 30
		List<CardSnapshot> a = repeated(input("Lava", 11, 100), 1);
		List<CardSnapshot> b = repeated(input("Lava", 1, 1), 10);

		MatchResult result = play(a, b);

		assertThat(result.endReason()).isEqualTo(EndReason.ENERGY_THRESHOLD);
		assertThat(result.winner()).isEqualTo(Winner.A);
		assertThat(result.finalEnergy()).isEqualTo(new EnergyBySide(30, 0));
		assertThat(clashes(result)).hasSize(3);
		assertThat(result.events().getLast())
				.isEqualTo(new BattleFinished(result.events().size(), EndReason.ENERGY_THRESHOLD, Winner.A,
						new EnergyBySide(30, 0)));
	}

	@Test
	void match12_thresholdDependsOnTheMode() {
		List<CardInput> a = List.of(input("Lava", 11, 100));
		List<CardInput> b = List.of(input("Lava", 1, 1), input("Lava", 1, 1), input("Lava", 1, 1));

		MatchResult quick = engine.play(new MatchRequest(a, b, MatchMode.QUICK, 1, OFFICIAL));
		MatchResult ranked = engine.play(new MatchRequest(a, b, MatchMode.RANKED, 1, OFFICIAL));
		MatchResult friend = engine.play(new MatchRequest(a, b, MatchMode.FRIEND, 1, OFFICIAL));

		assertThat(quick.endReason()).isEqualTo(EndReason.ENERGY_THRESHOLD);
		assertThat(quick.finalEnergy()).isEqualTo(new EnergyBySide(20, 0));
		assertThat(ranked.endReason()).isEqualTo(EndReason.ENERGY_THRESHOLD);
		assertThat(ranked.finalEnergy()).isEqualTo(new EnergyBySide(30, 0));
		assertThat(friend.endReason()).isEqualTo(EndReason.ENERGY_THRESHOLD);
		assertThat(friend.finalEnergy()).isEqualTo(new EnergyBySide(30, 0));
	}

	@Test
	void match12_thresholdIsReachedAlsoWhenExceeded() {
		MatchResult result = play(ordered(input("Lava", 50, 100)), ordered(input("Lava", 1, 5)));

		assertThat(result.endReason()).isEqualTo(EndReason.ENERGY_THRESHOLD);
		assertThat(result.finalEnergy()).isEqualTo(new EnergyBySide(45, 0));
	}

	@Test
	void match13and14_deckExhaustionGivesTheBonusForEveryCardLeftIncludingTheOneInTheArena() {
		// A: one card that survives both clashes; B: two cards, both defeated with 1 excess damage each
		List<CardSnapshot> a = ordered(input("Lava", 5, 100), input("Lava", 1, 1), input("Lava", 1, 1));
		List<CardSnapshot> b = ordered(input("Lava", 1, 4), input("Lava", 1, 4));

		MatchResult result = play(a, b);

		DeckExhausted exhausted = result.events().stream().filter(DeckExhausted.class::isInstance)
				.map(DeckExhausted.class::cast).findFirst().orElseThrow();
		assertThat(exhausted.exhaustedSide()).isEqualTo(ExhaustedSide.B);
		assertThat(exhausted.remainingCardsBySide()).isEqualTo(new EnergyBySide(3, 0));
		assertThat(exhausted.bonusPerCard()).isEqualTo(4);
		assertThat(exhausted.bonusAwardedBySide()).isEqualTo(new EnergyBySide(12, 0));
		assertThat(exhausted.energyAfter()).isEqualTo(new EnergyBySide(14, 0));
		assertThat(result.endReason()).isEqualTo(EndReason.DECK_EXHAUSTED);
		assertThat(result.winner()).isEqualTo(Winner.A);
		assertThat(result.finalEnergy()).isEqualTo(new EnergyBySide(14, 0));
		assertThat(deployments(result)).as("A does not deploy once B is exhausted").hasSize(3);
	}

	@Test
	void match13_bothExhaustedWhenBothDefeatedWithEmptyDecks() {
		MatchResult result = play(ordered(input("Lava", 9, 5)), ordered(input("Lava", 9, 5)));

		DeckExhausted exhausted = result.events().stream().filter(DeckExhausted.class::isInstance)
				.map(DeckExhausted.class::cast).findFirst().orElseThrow();
		assertThat(exhausted.exhaustedSide()).isEqualTo(ExhaustedSide.BOTH);
		assertThat(exhausted.remainingCardsBySide()).isEqualTo(new EnergyBySide(0, 0));
		assertThat(result.winner()).isEqualTo(Winner.DRAW);
	}

	@Test
	void match13_thresholdHasPriorityOverDeckExhaustionInTheSameClash() {
		// B's last card is defeated with 30 excess damage: A wins by threshold, no DECK_EXHAUSTED event
		MatchResult result = play(ordered(input("Lava", 31, 100)), ordered(input("Lava", 1, 1)));

		assertThat(result.endReason()).isEqualTo(EndReason.ENERGY_THRESHOLD);
		assertThat(result.events()).noneMatch(DeckExhausted.class::isInstance);
		assertThat(result.finalEnergy()).isEqualTo(new EnergyBySide(30, 0));
	}

	@Test
	void match15_sameEnergyIsADraw() {
		// both creatures are defeated at exactly 0, both decks are empty: 0 Energy each
		MatchResult result = play(ordered(input("Lava", 5, 5)), ordered(input("Lava", 5, 5)));

		assertThat(result.winner()).isEqualTo(Winner.DRAW);
		assertThat(result.endReason()).isEqualTo(EndReason.DECK_EXHAUSTED);
		assertThat(result.finalEnergy()).isEqualTo(new EnergyBySide(0, 0));
	}

	@Test
	void match15_drawAfterTheBonusToo() {
		// clash 1: B gains 4; clash 2: both defeated; clash 3: B is exhausted and A still has 1 card (bonus 4)
		MatchResult result = play(ordered(input("Lava", 1, 1), input("Lava", 100, 1), input("Lava", 1, 1)),
				ordered(input("Lava", 5, 100)));

		DeckExhausted exhausted = result.events().stream().filter(DeckExhausted.class::isInstance)
				.map(DeckExhausted.class::cast).findFirst().orElseThrow();
		assertThat(exhausted.energyAfter()).isEqualTo(new EnergyBySide(4, 4));
		assertThat(result.winner()).isEqualTo(Winner.DRAW);
	}

	@Test
	void match16_bonusPastTheThresholdIsNotAWinByThreshold() {
		// A gains 29 Energy, then B is exhausted: A keeps 8 cards (32 Energy), ends at 61 but by exhaustion
		List<CardSnapshot> a = repeated(input("Lava", 30, 100), 9);
		List<CardSnapshot> b = ordered(input("Lava", 1, 1));

		MatchResult result = play(a, b);

		assertThat(result.endReason()).isEqualTo(EndReason.DECK_EXHAUSTED);
		assertThat(result.winner()).isEqualTo(Winner.A);
		assertThat(result.finalEnergy()).isEqualTo(new EnergyBySide(29 + 9 * 4, 0));
	}

	@Test
	void match17_everyMatchEndsWithinFiftyNineClashes() {
		List<CardInput> a = Decks.fullDeck(1);
		List<CardInput> b = Decks.fullDeck(100);

		for (long seed = 0; seed < 200; seed++) {
			MatchResult result = engine.play(new MatchRequest(a, b, MatchMode.RANKED, seed, OFFICIAL));
			assertThat(result.events().getLast()).isInstanceOf(BattleFinished.class);
			assertThat(clashes(result).size()).as("seed %d", seed).isLessThanOrEqualTo(59);
		}
	}

	@Test
	void match02_sameSeedGivesTheSameMatch() {
		MatchRequest request = new MatchRequest(Decks.fullDeck(1), Decks.fullDeck(100), MatchMode.RANKED, 42, OFFICIAL);

		MatchResult first = engine.play(request);
		MatchResult second = engine.play(request);

		assertThat(second).isEqualTo(first);
		assertThat(first.shuffledDeckA()).extracting(CardSnapshot::deckPosition)
				.containsExactlyElementsOf(java.util.stream.IntStream.range(0, 30).boxed().toList());
		assertThat(first.shuffledDeckA()).extracting(CardSnapshot::cardId)
				.as("the shuffle changed the order").isNotEqualTo(request.deckA().stream().map(CardInput::cardId).toList());
		assertThat(first.shuffledDeckA()).extracting(CardSnapshot::cardId)
				.containsExactlyInAnyOrderElementsOf(request.deckA().stream().map(CardInput::cardId).toList());
	}

	@Test
	void match02_differentSeedsGiveADifferentOrder() {
		List<CardInput> a = Decks.fullDeck(1);
		List<CardInput> b = Decks.fullDeck(100);

		MatchResult one = engine.play(new MatchRequest(a, b, MatchMode.RANKED, 1, OFFICIAL));
		MatchResult two = engine.play(new MatchRequest(a, b, MatchMode.RANKED, 2, OFFICIAL));

		assertThat(one.shuffledDeckA()).isNotEqualTo(two.shuffledDeckA());
	}

	@Test
	void eventsAreNumberedFromOneWithoutGaps() {
		MatchResult result = engine.play(new MatchRequest(Decks.fullDeck(1), Decks.fullDeck(100), MatchMode.RANKED, 7, OFFICIAL));

		for (int i = 0; i < result.events().size(); i++) {
			assertThat(result.events().get(i).sequence()).isEqualTo(i + 1);
		}
		assertThat(result.events().getLast()).isInstanceOf(BattleFinished.class);
		BattleFinished finished = (BattleFinished) result.events().getLast();
		assertThat(finished.winnerSide()).isEqualTo(result.winner());
		assertThat(finished.endReason()).isEqualTo(result.endReason());
		assertThat(finished.finalEnergyBySide()).isEqualTo(result.finalEnergy());
	}
}
