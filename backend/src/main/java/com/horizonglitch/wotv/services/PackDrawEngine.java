package com.horizonglitch.wotv.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Pure pack odds and pity logic, independent from persistence. */
public final class PackDrawEngine {
    public enum PackKind { STANDARD, EVENT }

    public record Rarity(int id, String name, int rank) {
        public Rarity {
            if (id <= 0 || name == null || name.isBlank() || rank < 0)
                throw new IllegalArgumentException("Invalid rarity");
        }
    }

    /** Probability is expressed as a percentage; outcomes for a slot must total 100. */
    public record Outcome(Rarity rarity, double probabilityPercent) {
        public Outcome {
            Objects.requireNonNull(rarity, "rarity");
            if (!Double.isFinite(probabilityPercent) || probabilityPercent <= 0 || probabilityPercent > 100)
                throw new IllegalArgumentException("Probability must be in (0, 100]");
        }
    }

    public record Slot(int position, int cardCount, List<Outcome> outcomes) {
        public Slot {
            if (position < 1 || cardCount < 1) throw new IllegalArgumentException("Invalid slot");
            outcomes = List.copyOf(outcomes);
            if (outcomes.isEmpty() || Math.abs(outcomes.stream().mapToDouble(Outcome::probabilityPercent).sum() - 100) > 0.0001)
                throw new IllegalArgumentException("Slot outcome probabilities must total 100");
            if (outcomes.stream().map(o -> o.rarity().id()).distinct().count() != outcomes.size())
                throw new IllegalArgumentException("A rarity can occur only once per slot");
        }

        public boolean canGuarantee(int minimumRank) {
            return outcomes.stream().anyMatch(o -> o.rarity().rank() >= minimumRank);
        }
    }

    public record Guarantee(int minimumRank, int everyPacks) {
        public Guarantee {
            if (minimumRank < 0 || everyPacks < 1) throw new IllegalArgumentException("Invalid guarantee");
        }
    }

    /** Consecutive misses for each guarantee; a qualifying result resets that counter. */
    public record Pity(int championMisses, int electMisses, int wandererMisses) {
        public Pity {
            if (championMisses < 0 || electMisses < 0 || wandererMisses < 0)
                throw new IllegalArgumentException("Pity counters cannot be negative");
        }
    }

    public record DrawnCard(int slotPosition, Rarity rarity, boolean guaranteed) { }
    public record DrawResult(List<DrawnCard> cards, Pity pity) {
        public DrawResult { cards = List.copyOf(cards); }
    }

    private final RandomGenerator random;

    public PackDrawEngine(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public DrawResult draw(PackKind kind, List<Slot> slots, Pity current,
                           Guarantee champion, Guarantee elect, Guarantee wanderer) {
        Objects.requireNonNull(kind); Objects.requireNonNull(current);
        if (slots == null || slots.isEmpty()) throw new IllegalArgumentException("A pack needs at least one slot");
        var orderedSlots = slots.stream().sorted(Comparator.comparingInt(Slot::position)).toList();
        var chosen = new ArrayList<DrawnCard>();

        boolean forceChampion = current.championMisses() >= champion.everyPacks() - 1;
        boolean forceElect = kind == PackKind.STANDARD && current.electMisses() >= elect.everyPacks() - 1;
        boolean forceWanderer = kind == PackKind.EVENT && current.wandererMisses() >= wanderer.everyPacks() - 1;
        boolean championDone = false, electDone = false, wandererDone = false;

        for (Slot slot : orderedSlots) {
            for (int i = 0; i < slot.cardCount(); i++) {
                boolean needChampion = forceChampion && !championDone && slot.canGuarantee(champion.minimumRank());
                int minimumRank = Math.max(needChampion ? champion.minimumRank() : -1,
                        Math.max(forceElect && !electDone && slot.canGuarantee(elect.minimumRank()) ? elect.minimumRank() : -1,
                                forceWanderer && !wandererDone && slot.canGuarantee(wanderer.minimumRank()) ? wanderer.minimumRank() : -1));
                Rarity rarity = pick(slot.outcomes(), minimumRank);
                boolean wasGuaranteed = minimumRank >= 0;
                chosen.add(new DrawnCard(slot.position(), rarity, wasGuaranteed));
                championDone |= rarity.rank() >= champion.minimumRank();
                electDone |= kind == PackKind.STANDARD && rarity.rank() >= elect.minimumRank();
                wandererDone |= kind == PackKind.EVENT && rarity.rank() >= wanderer.minimumRank();
            }
        }
        if (forceChampion && !championDone) throw new IllegalArgumentException("No slot can satisfy Champion guarantee");
        if (kind == PackKind.STANDARD && forceElect && !electDone) throw new IllegalArgumentException("No slot can satisfy Elect guarantee");
        if (kind == PackKind.EVENT && forceWanderer && !wandererDone) throw new IllegalArgumentException("No slot can satisfy Wanderer guarantee");

        boolean gotChampion = chosen.stream().anyMatch(c -> c.rarity().rank() >= champion.minimumRank());
        boolean gotElect = chosen.stream().anyMatch(c -> c.rarity().rank() >= elect.minimumRank());
        boolean gotWanderer = chosen.stream().anyMatch(c -> c.rarity().rank() >= wanderer.minimumRank());
        var next = new Pity(gotChampion ? 0 : current.championMisses() + 1,
                kind == PackKind.STANDARD ? (gotElect ? 0 : current.electMisses() + 1) : current.electMisses(),
                kind == PackKind.EVENT ? (gotWanderer ? 0 : current.wandererMisses() + 1) : current.wandererMisses());
        return new DrawResult(chosen, next);
    }

    private Rarity pick(List<Outcome> outcomes, int minimumRank) {
        var eligible = outcomes.stream().filter(o -> o.rarity().rank() >= minimumRank).toList();
        if (eligible.isEmpty()) {
            if (minimumRank >= 0) throw new IllegalArgumentException("No outcome can satisfy guarantee");
            eligible = outcomes;
        }
        double total = eligible.stream().mapToDouble(Outcome::probabilityPercent).sum();
        double draw = random.nextDouble(total);
        for (Outcome outcome : eligible) {
            draw -= outcome.probabilityPercent();
            if (draw < 0) return outcome.rarity();
        }
        return eligible.getLast().rarity();
    }
}
