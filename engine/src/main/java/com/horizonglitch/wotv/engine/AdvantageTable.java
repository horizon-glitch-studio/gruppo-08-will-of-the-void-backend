package com.horizonglitch.wotv.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The faction advantages (FAC-01 to FAC-05): for every pair (faction that prevails, faction overpowered), the ATK
 * bonus of the creature that prevails. A pair not in the table is even. An inert faction (Gem, Noble Gas) never
 * appears, so it never gives or receives an advantage.
 *
 * <p>The API builds it from the {@code faction_advantage} table; {@link #official(int)} holds the twelve
 * advantages of rules/factions.md for tests and defaults.
 */
public final class AdvantageTable {

	/** The faction {@code prevails} gets {@code bonus} ATK against the faction {@code over}. */
	public record Advantage(String prevails, String over, int bonus) {
	}

	private static final List<String[]> OFFICIAL_PAIRS = List.of(
			new String[] {"Lava", "Metal"}, new String[] {"Lava", "Matte"},
			new String[] {"Electro", "Lava"}, new String[] {"Electro", "Ice"},
			new String[] {"Radio", "Lava"}, new String[] {"Radio", "Matte"},
			new String[] {"Ice", "Radio"}, new String[] {"Ice", "Metal"},
			new String[] {"Metal", "Electro"}, new String[] {"Metal", "Radio"},
			new String[] {"Matte", "Electro"}, new String[] {"Matte", "Ice"});

	private final Map<String, Map<String, Integer>> bonuses;

	private AdvantageTable(Map<String, Map<String, Integer>> bonuses) {
		this.bonuses = bonuses;
	}

	public static AdvantageTable of(List<Advantage> advantages) {
		Map<String, Map<String, Integer>> bonuses = new LinkedHashMap<>();
		for (Advantage a : advantages) {
			if (a.bonus() < 0) {
				throw new IllegalArgumentException("An advantage never lowers ATK (FAC-03)");
			}
			bonuses.computeIfAbsent(a.prevails(), k -> new LinkedHashMap<>()).put(a.over(), a.bonus());
		}
		bonuses.replaceAll((k, v) -> Collections.unmodifiableMap(v));
		return new AdvantageTable(Collections.unmodifiableMap(bonuses));
	}

	/** No advantage at all: every clash is even. */
	public static AdvantageTable none() {
		return new AdvantageTable(Map.of());
	}

	/** The twelve advantages of rules/factions.md, each worth {@code advantageValue} ATK (ADVANTAGE_VALUE). */
	public static AdvantageTable official(int advantageValue) {
		return of(OFFICIAL_PAIRS.stream().map(p -> new Advantage(p[0], p[1], advantageValue)).toList());
	}

	/** The ATK bonus of {@code faction} against {@code opposing}; 0 when it does not prevail (FAC-02, FAC-04). */
	public int bonus(String faction, String opposing) {
		return bonuses.getOrDefault(faction, Map.of()).getOrDefault(opposing, 0);
	}
}
