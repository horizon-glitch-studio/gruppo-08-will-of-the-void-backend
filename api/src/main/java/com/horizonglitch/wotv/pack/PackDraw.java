package com.horizonglitch.wotv.pack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Draws the cards of a pack (PACK-05, PACK-06) and applies the guarantees (PACK-08 to PACK-12), as a pure
 * function: the slots, the counters and the random source come in, the cards and the new counters go out.
 */
public final class PackDraw {

	private PackDraw() {
	}

	public record RarityOdds(int rarityId, BigDecimal probability) {
	}

	public record SlotOdds(int position, List<RarityOdds> odds) {
	}

	/** The guarantee counters of the player before the pack. */
	public record Counters(int withoutChampion, int standardWithoutElect, int eventWithoutWanderer) {
	}

	/** The pack in a row that gets each guarantee: 10, 50 and 50 in the rules. */
	public record Thresholds(int champion, int elect, int wanderer) {
	}

	/**
	 * The rarities the guarantees refer to. "Champion or rarer" means a sort order at least the Champion one;
	 * {@code sortOrders} maps every rarity id that can be drawn to its sort order.
	 */
	public record Rarities(int championId, int electId, int wandererId, Map<Integer, Integer> sortOrders) {

		boolean championOrRarer(int rarityId) {
			return sortOrders.getOrDefault(rarityId, Integer.MIN_VALUE) >= sortOrders.get(championId);
		}
	}

	/** The cards that can come out of a rarity: in an event pack, the Wanderers of that event. */
	@FunctionalInterface
	public interface Candidates {
		List<Long> cardsOf(int rarityId);
	}

	public record Drawn(int position, int rarityId, long cardId) {
	}

	/** {@code guarantee} is the guarantee that replaced the last slot, or null when the luck was enough. */
	public record Result(List<Drawn> cards, GuaranteeType guarantee, Counters counters) {
	}

	/** Thrown when a rarity that can be drawn has no card: the catalog or the event is incomplete. */
	public static class NoCardsException extends RuntimeException {
		public NoCardsException(int rarityId) {
			super("No card can be drawn for rarity " + rarityId);
		}
	}

	public static Result open(List<SlotOdds> slots, boolean event, Counters counters, Thresholds thresholds,
			Rarities rarities, Candidates candidates, RandomGenerator random) {
		List<Drawn> cards = new ArrayList<>();
		for (SlotOdds slot : slots) {
			int rarityId = pickRarity(slot.odds(), random);
			cards.add(new Drawn(slot.position(), rarityId, pickCard(rarityId, candidates, random)));
		}

		GuaranteeType due = due(event, counters, thresholds);
		GuaranteeType applied = null;
		if (due != null && !met(due, cards, rarities) && !cards.isEmpty()) {
			int rarityId = switch (due) {
				case WANDERER -> rarities.wandererId();
				case ELECT -> rarities.electId();
				case CHAMPION -> rarities.championId();
			};
			Drawn last = cards.get(cards.size() - 1);
			cards.set(cards.size() - 1, new Drawn(last.position(), rarityId, pickCard(rarityId, candidates, random)));
			applied = due;
		}

		boolean championOrRarer = cards.stream().anyMatch(d -> rarities.championOrRarer(d.rarityId()));
		boolean elect = cards.stream().anyMatch(d -> d.rarityId() == rarities.electId());
		boolean wanderer = cards.stream().anyMatch(d -> d.rarityId() == rarities.wandererId());
		Counters next = new Counters(
				championOrRarer ? 0 : counters.withoutChampion() + 1,
				event ? counters.standardWithoutElect() : (elect ? 0 : counters.standardWithoutElect() + 1),
				event ? (wanderer ? 0 : counters.eventWithoutWanderer() + 1) : counters.eventWithoutWanderer());
		return new Result(List.copyOf(cards), applied, next);
	}

	/** The rarest guarantee due on this pack (PACK-12): Wanderer, then Elect, then Champion. */
	static GuaranteeType due(boolean event, Counters c, Thresholds t) {
		if (event && c.eventWithoutWanderer() + 1 >= t.wanderer()) {
			return GuaranteeType.WANDERER;
		}
		if (!event && c.standardWithoutElect() + 1 >= t.elect()) {
			return GuaranteeType.ELECT;
		}
		if (c.withoutChampion() + 1 >= t.champion()) {
			return GuaranteeType.CHAMPION;
		}
		return null;
	}

	private static boolean met(GuaranteeType due, List<Drawn> cards, Rarities rarities) {
		return switch (due) {
			case WANDERER -> cards.stream().anyMatch(d -> d.rarityId() == rarities.wandererId());
			case ELECT -> cards.stream().anyMatch(d -> d.rarityId() == rarities.electId());
			case CHAMPION -> cards.stream().anyMatch(d -> rarities.championOrRarer(d.rarityId()));
		};
	}

	private static int pickRarity(List<RarityOdds> odds, RandomGenerator random) {
		double total = odds.stream().mapToDouble(o -> o.probability().doubleValue()).sum();
		double roll = random.nextDouble() * total;
		double cumulative = 0;
		for (RarityOdds o : odds) {
			cumulative += o.probability().doubleValue();
			if (roll < cumulative) {
				return o.rarityId();
			}
		}
		return odds.get(odds.size() - 1).rarityId();
	}

	private static long pickCard(int rarityId, Candidates candidates, RandomGenerator random) {
		List<Long> ids = candidates.cardsOf(rarityId);
		if (ids.isEmpty()) {
			throw new NoCardsException(rarityId);
		}
		return ids.get(random.nextInt(ids.size()));
	}
}
