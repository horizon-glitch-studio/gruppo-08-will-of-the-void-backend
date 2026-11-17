package com.horizonglitch.wotv.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * The log produced by the engine must follow docs/battle-viewer.schema.json: the events are stored as they are in
 * battle_log.turn_log and the shuffled decks in battle.decks_snapshot. The two example documents in docs/ are
 * replayed with their draw order and must come out identical.
 */
class BattleViewerLogTest {

	private static final Path DOCS = Path.of("..", "docs");

	private final ObjectMapper mapper = new ObjectMapper();
	private final MatchEngine engine = new MatchEngine();

	@Test
	void aPlayedMatchSerializesToAValidBattleViewerDocument() throws IOException {
		MatchRequest request = new MatchRequest(Decks.fullDeck(1), Decks.fullDeck(100), MatchMode.RANKED, 42, Decks.OFFICIAL);
		MatchResult result = engine.play(request);

		JsonNode document = mapper.valueToTree(document(result, "RANKED", 30, 4));

		assertThat(validate(document)).isEmpty();
	}

	@Test
	void aMatchEndedByExhaustionSerializesToAValidBattleViewerDocument() throws IOException {
		// ATK 1 everywhere: every creature is defeated at exactly 0, nobody gains Energy and the decks run out
		List<CardInput> a = new ArrayList<>();
		List<CardInput> b = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			a.add(new CardInput(1 + i, "A " + i, "Gem", "Elect", 1, 3 + i % 3));
			b.add(new CardInput(100 + i, "B " + i, "Noble Gas", "Wanderer", 1, 3 + i % 2));
		}
		MatchResult result = engine.play(new MatchRequest(a, b, MatchMode.QUICK, 3, Decks.OFFICIAL));
		assertThat(result.endReason()).isEqualTo(EndReason.DECK_EXHAUSTED);

		JsonNode document = mapper.valueToTree(document(result, "QUICK", 20, 4));

		assertThat(validate(document)).isEmpty();
	}

	@Test
	void theExampleDocumentsAreValidAgainstTheSchema() throws IOException {
		for (String name : List.of("battle-viewer.example.json", "battle-viewer.example-deck-exhausted.json")) {
			assertThat(validate(mapper.readTree(DOCS.resolve(name).toFile()))).as(name).isEmpty();
		}
	}

	@Test
	void theValidatorRejectsADocumentThatBreaksTheSchema() throws IOException {
		MatchResult result = engine.play(new MatchRequest(Decks.fullDeck(1), Decks.fullDeck(100), MatchMode.RANKED, 42, Decks.OFFICIAL));
		Map<String, Object> document = document(result, "RANKED", 30, 4);
		document.remove("result");

		JsonNode broken = mapper.valueToTree(document);

		assertThat(validate(broken)).isNotEmpty();
	}

	@Test
	void theEngineReproducesTheThresholdExample() throws IOException {
		assertReproduces("battle-viewer.example.json");
	}

	@Test
	void theEngineReproducesTheDeckExhaustedExample() throws IOException {
		assertReproduces("battle-viewer.example-deck-exhausted.json");
	}

	private void assertReproduces(String name) throws IOException {
		JsonNode example = mapper.readTree(DOCS.resolve(name).toFile());
		List<CardSnapshot> deckA = snapshots(example, "A");
		List<CardSnapshot> deckB = snapshots(example, "B");
		int threshold = example.get("rules").get("energyWinThreshold").asInt();
		int bonus = example.get("rules").get("endBonusPerCard").asInt();
		MatchParameters parameters = new MatchParameters(threshold, threshold, threshold, bonus, AdvantageTable.official(3));

		MatchResult result = engine.simulate(deckA, deckB, threshold, parameters);

		JsonNode events = mapper.valueToTree(result.events());
		JsonNode summary = mapper.valueToTree(Map.of("endReason", result.endReason(), "winnerSide", result.winner(),
				"finalEnergyBySide", result.finalEnergy()));
		assertThat(events).isEqualTo(example.get("events"));
		assertThat(summary).isEqualTo(example.get("result"));
	}

	private List<CardSnapshot> snapshots(JsonNode example, String side) throws IOException {
		for (JsonNode player : example.get("players")) {
			if (player.get("side").asText().equals(side)) {
				JsonNode cards = player.get("deckSnapshot").get("cards");
				return List.of(mapper.treeToValue(cards, CardSnapshot[].class));
			}
		}
		throw new IllegalArgumentException("no side " + side);
	}

	/** Builds the document as the replay API will: the engine output plus what only the server knows. */
	private static Map<String, Object> document(MatchResult result, String mode, int threshold, int bonus) {
		Map<String, Object> document = new LinkedHashMap<>();
		document.put("schemaVersion", "1.0");
		document.put("battleId", 1);
		document.put("mode", mode);
		document.put("rules", Map.of("energyWinThreshold", threshold, "endBonusPerCard", bonus));
		document.put("players", List.of(player("A", 101, "star_hunter", result.shuffledDeckA()),
				player("B", 202, "void_walker", result.shuffledDeckB())));
		document.put("events", result.events());
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("endReason", result.endReason());
		summary.put("winnerSide", result.winner());
		summary.put("finalEnergyBySide", result.finalEnergy());
		document.put("result", summary);
		return document;
	}

	private static Map<String, Object> player(String side, long playerId, String displayName, List<CardSnapshot> deck) {
		Map<String, Object> player = new LinkedHashMap<>();
		player.put("side", side);
		player.put("playerId", playerId);
		player.put("displayName", displayName);
		player.put("deckSnapshot", Map.of("shuffleApplied", true, "cards", deck));
		return player;
	}

	private Set<ValidationMessage> validate(JsonNode document) throws IOException {
		JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
		JsonSchema schema = factory.getSchema(Files.readString(DOCS.resolve("battle-viewer.schema.json")));
		return schema.validate(document);
	}
}
