package com.horizonglitch.wotv.services;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PackDrawEngineTest {
    private static final PackDrawEngine.Rarity COMMON = new PackDrawEngine.Rarity(1, "Common", 1);
    private static final PackDrawEngine.Rarity ELECT = new PackDrawEngine.Rarity(2, "Elect", 2);
    private static final PackDrawEngine.Rarity CHAMPION = new PackDrawEngine.Rarity(3, "Champion", 3);
    private static final PackDrawEngine.Rarity WANDERER = new PackDrawEngine.Rarity(4, "Wanderer", 4);
    private static final List<PackDrawEngine.Slot> SLOTS = List.of(new PackDrawEngine.Slot(1, 1, List.of(
            new PackDrawEngine.Outcome(COMMON, 80),
            new PackDrawEngine.Outcome(ELECT, 15),
            new PackDrawEngine.Outcome(CHAMPION, 4),
            new PackDrawEngine.Outcome(WANDERER, 1))));
    private static final PackDrawEngine.Guarantee CHAMPION_GUARANTEE = new PackDrawEngine.Guarantee(3, 10);
    private static final PackDrawEngine.Guarantee ELECT_GUARANTEE = new PackDrawEngine.Guarantee(2, 50);
    private static final PackDrawEngine.Guarantee WANDERER_GUARANTEE = new PackDrawEngine.Guarantee(4, 50);

    @Test
    void observedDistributionMatchesConfiguredOdds() {
        var engine = new PackDrawEngine(new Random(20261116));
        var pity = new PackDrawEngine.Pity(0, 0, 0);
        int electOrBetter = 0, championOrBetter = 0, wanderers = 0;
        int draws = 100_000;
        var looseGuarantees = new PackDrawEngine.Guarantee(3, draws + 1);
        var looseElect = new PackDrawEngine.Guarantee(2, draws + 1);
        var looseWanderer = new PackDrawEngine.Guarantee(4, draws + 1);
        for (int i = 0; i < draws; i++) {
            var result = engine.draw(PackDrawEngine.PackKind.STANDARD, SLOTS, pity,
                    looseGuarantees, looseElect, looseWanderer);
            pity = result.pity();
            var rarity = result.cards().getFirst().rarity();
            if (rarity.rank() >= 2) electOrBetter++;
            if (rarity.rank() >= 3) championOrBetter++;
            if (rarity.equals(WANDERER)) wanderers++;
        }
        assertEquals(0.20, (double) electOrBetter / draws, 0.015);
        assertEquals(0.05, (double) championOrBetter / draws, 0.012);
        assertEquals(0.01, (double) wanderers / draws, 0.008);
    }

    @Test
    void guaranteesAreAppliedAtTheConfiguredOpeningAndCountersReset() {
        var engine = new PackDrawEngine(new Random(1));
        var pity = new PackDrawEngine.Pity(0, 0, 0);
        var misses = new PackDrawEngine.Pity(9, 0, 0);
        var guaranteed = engine.draw(PackDrawEngine.PackKind.STANDARD, List.of(new PackDrawEngine.Slot(1, 1,
                        List.of(new PackDrawEngine.Outcome(COMMON, 99), new PackDrawEngine.Outcome(CHAMPION, 1)))),
                misses, CHAMPION_GUARANTEE, ELECT_GUARANTEE, WANDERER_GUARANTEE);
        assertTrue(guaranteed.cards().getFirst().guaranteed());
        assertTrue(guaranteed.cards().getFirst().rarity().rank() >= 3);
        assertEquals(0, guaranteed.pity().championMisses());
    }

    @Test
    void electAndWandererGuaranteesTriggerOnTheirOwnPackTypeCounters() {
        var engine = new PackDrawEngine(new Random(2));
        var electOnly = List.of(new PackDrawEngine.Slot(1, 1, List.of(
                new PackDrawEngine.Outcome(COMMON, 99), new PackDrawEngine.Outcome(ELECT, 1))));
        var elect = engine.draw(PackDrawEngine.PackKind.STANDARD, electOnly,
                new PackDrawEngine.Pity(0, 49, 0), CHAMPION_GUARANTEE, ELECT_GUARANTEE, WANDERER_GUARANTEE);
        assertTrue(elect.cards().getFirst().guaranteed());
        assertEquals(ELECT, elect.cards().getFirst().rarity());
        assertEquals(0, elect.pity().electMisses());

        var wandererOnly = List.of(new PackDrawEngine.Slot(1, 1, List.of(
                new PackDrawEngine.Outcome(COMMON, 99), new PackDrawEngine.Outcome(WANDERER, 1))));
        var wanderer = engine.draw(PackDrawEngine.PackKind.EVENT, wandererOnly,
                new PackDrawEngine.Pity(0, 0, 49), CHAMPION_GUARANTEE, ELECT_GUARANTEE, WANDERER_GUARANTEE);
        assertTrue(wanderer.cards().getFirst().guaranteed());
        assertEquals(WANDERER, wanderer.cards().getFirst().rarity());
        assertEquals(0, wanderer.pity().wandererMisses());
    }

    @Test
    void standardAndEventCountersAdvanceIndependently() {
        var engine = new PackDrawEngine(new Random(17));
        var commonOnly = List.of(new PackDrawEngine.Slot(1, 1,
                List.of(new PackDrawEngine.Outcome(COMMON, 97), new PackDrawEngine.Outcome(ELECT, 1),
                        new PackDrawEngine.Outcome(CHAMPION, 1), new PackDrawEngine.Outcome(WANDERER, 1))));
        var firstStandard = engine.draw(PackDrawEngine.PackKind.STANDARD, commonOnly,
                new PackDrawEngine.Pity(0, 0, 7), CHAMPION_GUARANTEE, ELECT_GUARANTEE, WANDERER_GUARANTEE);
        assertEquals(7, firstStandard.pity().wandererMisses());
        var firstEvent = engine.draw(PackDrawEngine.PackKind.EVENT, commonOnly,
                firstStandard.pity(), CHAMPION_GUARANTEE, ELECT_GUARANTEE, WANDERER_GUARANTEE);
        assertEquals(1, firstEvent.pity().electMisses());
        assertEquals(8, firstEvent.pity().wandererMisses());
    }

    @Test
    void rejectsProbabilityTablesThatDoNotSumToOneHundred() {
        assertThrows(IllegalArgumentException.class, () -> new PackDrawEngine.Slot(1, 1,
                List.of(new PackDrawEngine.Outcome(COMMON, 99))));
    }
}
