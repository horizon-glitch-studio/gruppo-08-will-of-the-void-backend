package com.horizonglitch.wotv;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The data of V2__reference_data.sql match the rules repository. Other tests add their own rows with different
 * names, so every query filters on the names of the rules.
 */
@SpringBootTest
class ReferenceDataTest {

	private static final List<String> ELEMENTAL = List.of("Lava", "Electro", "Radio", "Ice", "Metal", "Matte");

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void eightFactionsTwoOfThemInert() {
		List<String> inert = jdbc.queryForList("select name from faction where is_inert and name in "
				+ "('Lava', 'Electro', 'Radio', 'Ice', 'Metal', 'Matte', 'Gem', 'Noble Gas') order by name", String.class);
		assertThat(inert).containsExactly("Gem", "Noble Gas");
		assertThat(jdbc.queryForObject("select count(*) from faction where not is_inert and name in "
				+ "('Lava', 'Electro', 'Radio', 'Ice', 'Metal', 'Matte')", Integer.class)).isEqualTo(6);
	}

	@Test
	void everyElementalFactionPrevailsOverTwoAndIsOverpoweredByTwo() {
		List<Map<String, Object>> advantages = jdbc.queryForList("""
				select d.name as dominant, o.name as dominated, a.type, a.value
				from faction_advantage a
				join faction d on d.id = a.dominant_faction_id
				join faction o on o.id = a.dominated_faction_id
				where d.name in ('Lava', 'Electro', 'Radio', 'Ice', 'Metal', 'Matte', 'Gem', 'Noble Gas')
				""");
		assertThat(advantages).hasSize(12);
		assertThat(advantages).allSatisfy(a -> {
			assertThat(a.get("type")).isEqualTo("ATTACK");
			assertThat(a.get("value")).isEqualTo(3);
			assertThat(ELEMENTAL).contains((String) a.get("dominant"), (String) a.get("dominated"));
		});
		for (String faction : ELEMENTAL) {
			assertThat(advantages).filteredOn(a -> a.get("dominant").equals(faction)).hasSize(2);
			assertThat(advantages).filteredOn(a -> a.get("dominated").equals(faction)).hasSize(2);
		}
		assertThat(advantages).anySatisfy(a -> {
			assertThat(a.get("dominant")).isEqualTo("Lava");
			assertThat(a.get("dominated")).isEqualTo("Metal");
		});
	}

	@Test
	void sixRaritiesWithTheirPointsAndLimits() {
		List<Map<String, Object>> rarities = jdbc.queryForList("""
				select name, total_points, max_copies_per_deck, max_per_deck, duration_days, is_tradeable
				from rarity
				where name in ('Captive', 'Conscript', 'Soldier', 'Champion', 'Elect', 'Wanderer')
				order by sort_order
				""");
		assertThat(rarities).extracting(r -> r.get("name"))
				.containsExactly("Captive", "Conscript", "Soldier", "Champion", "Elect", "Wanderer");
		assertThat(rarities).extracting(r -> r.get("total_points")).containsExactly(12, 16, 20, 24, 28, 32);
		assertThat(rarities).extracting(r -> r.get("max_copies_per_deck")).containsExactly(3, 3, 2, 1, 1, 1);
		assertThat(rarities).extracting(r -> r.get("max_per_deck")).containsExactly(null, null, 8, 3, 2, 1);
		assertThat(rarities).extracting(r -> r.get("duration_days")).containsExactly(null, null, null, null, null, 7);
		assertThat(rarities).extracting(r -> r.get("is_tradeable")).containsExactly(true, true, true, true, true, false);
	}

	@Test
	void theStandardPackHasFiveSlotsThatEachAddUpTo100() {
		List<Map<String, Object>> slots = jdbc.queryForList("""
				select s.position, sum(r.probability) as total, string_agg(ra.name || ' ' || r.probability, ', '
				       order by ra.sort_order) as odds
				from pack_type p
				join pack_slot s on s.pack_type_id = p.id
				join pack_slot_rarity r on r.pack_type_id = s.pack_type_id and r.position = s.position
				join rarity ra on ra.id = r.rarity_id
				where p.name = 'Standard pack' and p.kind = 'STANDARD'
				group by s.position
				order by s.position
				""");
		assertThat(slots).hasSize(5);
		assertThat(slots).allSatisfy(s -> assertThat((BigDecimal) s.get("total")).isEqualByComparingTo("100"));
		assertThat(slots).extracting(s -> s.get("odds")).containsExactly(
				"Captive 70.00, Conscript 30.00",
				"Captive 70.00, Conscript 30.00",
				"Captive 70.00, Conscript 30.00",
				"Conscript 75.00, Soldier 25.00",
				"Soldier 84.00, Champion 12.50, Elect 3.50");
		assertThat(jdbc.queryForObject("select shard_cost from pack_type where name = 'Standard pack'", Integer.class))
				.isEqualTo(300);
	}
}
