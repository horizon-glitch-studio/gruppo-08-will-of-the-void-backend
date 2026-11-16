package com.horizonglitch.wotv.pack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** PACK-05 to PACK-12 without database, with the probabilities of the rules. */
class PackDrawTest {

	private static final int CAPTIVE = 1;
	private static final int CONSCRIPT = 2;
	private static final int SOLDIER = 3;
	private static final int CHAMPION = 4;
	private static final int ELECT = 5;
	private static final int WANDERER = 6;

	private static final PackDraw.Rarities RARITIES = new PackDraw.Rarities(CHAMPION, ELECT, WANDERER,
			Map.of(CAPTIVE, 1, CONSCRIPT, 2, SOLDIER, 3, CHAMPION, 4, ELECT, 5, WANDERER, 6));
	private static final PackDraw.Thresholds THRESHOLDS = new PackDraw.Thresholds(10, 50, 50);
	private static final PackDraw.Counters ZERO = new PackDraw.Counters(0, 0, 0);

	/** Card id = rarity id * 100: the rarity of a drawn card can be read from its id. */
	private static final PackDraw.Candidates CANDIDATES = rarityId -> List.of(rarityId * 100L, rarityId * 100L + 1);

	/** Always the lowest roll: the first rarity of every slot, the first candidate card. */
	private static final RandomGenerator LOWEST = () -> 0L;
	/** Always the highest roll: the last rarity of every slot. */
	private static final RandomGenerator HIGHEST = () -> -1L;

	@Test
	void everySlotGivesOneCardWithTheFirstRarityOnTheLowestRoll() {
		PackDraw.Result result = PackDraw.open(standard(), false, ZERO, THRESHOLDS, RARITIES, CANDIDATES, LOWEST);
		assertThat(result.cards()).extracting(PackDraw.Drawn::rarityId)
				.containsExactly(CAPTIVE, CAPTIVE, CAPTIVE, CONSCRIPT, SOLDIER);
		assertThat(result.guarantee()).isNull();
		assertThat(result.counters()).isEqualTo(new PackDraw.Counters(1, 1, 0));
	}

	@Test
	void theDrawFollowsTheSlotProbabilities() {
		RandomGenerator random = new SplittableRandom(42);
		int packs = 40_000;
		int captives = 0;
		int elects = 0;
		for (int i = 0; i < packs; i++) {
			PackDraw.Result result = PackDraw.open(standard(), false, ZERO, THRESHOLDS, RARITIES, CANDIDATES, random);
			captives += result.cards().get(0).rarityId() == CAPTIVE ? 1 : 0;
			elects += result.cards().get(4).rarityId() == ELECT ? 1 : 0;
		}
		assertThat(captives / (double) packs).isBetween(0.69, 0.71);
		assertThat(elects / (double) packs).isBetween(0.030, 0.040);
	}

	@Test
	void theTenthPackWithoutAChampionHasOneInTheLastSlot() {
		PackDraw.Result ninth = PackDraw.open(standard(), false, new PackDraw.Counters(8, 0, 0), THRESHOLDS, RARITIES,
				CANDIDATES, LOWEST);
		assertThat(ninth.guarantee()).isNull();

		PackDraw.Result tenth = PackDraw.open(standard(), false, new PackDraw.Counters(9, 0, 0), THRESHOLDS, RARITIES,
				CANDIDATES, LOWEST);
		assertThat(tenth.guarantee()).isEqualTo(GuaranteeType.CHAMPION);
		assertThat(tenth.cards()).hasSize(5);
		assertThat(tenth.cards().get(4).rarityId()).isEqualTo(CHAMPION);
		assertThat(tenth.counters().withoutChampion()).isZero();
	}

	@Test
	void aRareCardFromLuckNeedsNoGuarantee() {
		PackDraw.Result result = PackDraw.open(standard(), false, new PackDraw.Counters(9, 49, 0), THRESHOLDS,
				RARITIES, CANDIDATES, HIGHEST);
		assertThat(result.cards().get(4).rarityId()).isEqualTo(ELECT);
		assertThat(result.guarantee()).isNull();
		assertThat(result.counters()).isEqualTo(new PackDraw.Counters(0, 0, 0));
	}

	@Test
	void theRarestGuaranteeWins() {
		PackDraw.Result standard = PackDraw.open(standard(), false, new PackDraw.Counters(9, 49, 0), THRESHOLDS,
				RARITIES, CANDIDATES, LOWEST);
		assertThat(standard.guarantee()).isEqualTo(GuaranteeType.ELECT);
		assertThat(standard.counters()).isEqualTo(new PackDraw.Counters(0, 0, 0));

		PackDraw.Result event = PackDraw.open(event(), true, new PackDraw.Counters(9, 49, 49), THRESHOLDS, RARITIES,
				CANDIDATES, LOWEST);
		assertThat(event.guarantee()).isEqualTo(GuaranteeType.WANDERER);
		assertThat(event.cards().get(4).cardId()).isEqualTo(WANDERER * 100L);
		assertThat(event.counters()).isEqualTo(new PackDraw.Counters(0, 49, 0));
	}

	@Test
	void eachPackTypeCountsOnlyItsOwnGuarantee() {
		PackDraw.Result event = PackDraw.open(event(), true, new PackDraw.Counters(3, 20, 10), THRESHOLDS, RARITIES,
				CANDIDATES, LOWEST);
		assertThat(event.counters()).isEqualTo(new PackDraw.Counters(4, 20, 11));

		PackDraw.Result standard = PackDraw.open(standard(), false, new PackDraw.Counters(3, 20, 10), THRESHOLDS,
				RARITIES, CANDIDATES, LOWEST);
		assertThat(standard.counters()).isEqualTo(new PackDraw.Counters(4, 21, 10));
	}

	@Test
	void aRarityWithoutCardsCannotBeDrawn() {
		PackDraw.Candidates noWanderers = rarityId -> rarityId == WANDERER ? List.of() : CANDIDATES.cardsOf(rarityId);
		assertThatThrownBy(() -> PackDraw.open(event(), true, new PackDraw.Counters(0, 0, 49), THRESHOLDS, RARITIES,
				noWanderers, LOWEST)).isInstanceOf(PackDraw.NoCardsException.class);
	}

	static List<PackDraw.SlotOdds> standard() {
		return List.of(slot(1, CAPTIVE, "70", CONSCRIPT, "30"), slot(2, CAPTIVE, "70", CONSCRIPT, "30"),
				slot(3, CAPTIVE, "70", CONSCRIPT, "30"), slot(4, CONSCRIPT, "75", SOLDIER, "25"),
				new PackDraw.SlotOdds(5, List.of(odds(SOLDIER, "84"), odds(CHAMPION, "12.5"), odds(ELECT, "3.5"))));
	}

	static List<PackDraw.SlotOdds> event() {
		return List.of(slot(1, CAPTIVE, "70", CONSCRIPT, "30"), slot(2, CAPTIVE, "70", CONSCRIPT, "30"),
				slot(3, CAPTIVE, "70", CONSCRIPT, "30"), slot(4, CONSCRIPT, "75", SOLDIER, "25"),
				new PackDraw.SlotOdds(5, List.of(odds(SOLDIER, "84"), odds(CHAMPION, "11"), odds(ELECT, "1.5"),
						odds(WANDERER, "3.5"))));
	}

	private static PackDraw.SlotOdds slot(int position, int a, String pa, int b, String pb) {
		return new PackDraw.SlotOdds(position, List.of(odds(a, pa), odds(b, pb)));
	}

	private static PackDraw.RarityOdds odds(int rarityId, String probability) {
		return new PackDraw.RarityOdds(rarityId, new BigDecimal(probability));
	}
}
