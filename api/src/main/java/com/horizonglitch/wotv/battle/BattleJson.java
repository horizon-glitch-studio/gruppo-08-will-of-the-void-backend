package com.horizonglitch.wotv.battle;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.horizonglitch.wotv.engine.CardInput;
import com.horizonglitch.wotv.engine.CardSnapshot;
import com.horizonglitch.wotv.engine.MatchEvent;
import com.horizonglitch.wotv.engine.MatchResult;
import com.horizonglitch.wotv.engine.Side;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The JSON columns of a battle. {@code battle.decks_snapshot} holds the challenger's deck while the challenge is
 * pending and, once played, the rules and the two player snapshots of docs/battle-viewer.schema.json;
 * {@code battle_log.turn_log} holds the events of the engine as they are. The replay document is rebuilt from the two.
 */
@Component
public class BattleJson {

	static final String SCHEMA_VERSION = "1.0";

	private final ObjectMapper mapper;

	public BattleJson(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	/** {@code rules} of the schema: the values the match was played with, so old replays stay correct. */
	public record Rules(int energyWinThreshold, int endBonusPerCard) {
	}

	public record DeckSnapshot(boolean shuffleApplied, List<CardSnapshot> cards) {
	}

	public record PlayerSnapshot(Side side, long playerId, String displayName, DeckSnapshot deckSnapshot) {
	}

	/**
	 * Content of {@code decks_snapshot}: {@code challengerDeck} (not shuffled) for a challenge that was never
	 * played, {@code rules} and {@code players} for a PLAYED battle.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Snapshot(List<CardInput> challengerDeck, Rules rules, List<PlayerSnapshot> players) {
	}

	public String pendingSnapshot(List<CardInput> challengerDeck) {
		return mapper.writeValueAsString(new Snapshot(challengerDeck, null, null));
	}

	public String playedSnapshot(MatchSimulator.Played played, long challengerId, String challengerName,
			long opponentId, String opponentName) {
		MatchResult result = played.result();
		List<PlayerSnapshot> players = List.of(
				new PlayerSnapshot(Side.A, challengerId, challengerName, new DeckSnapshot(true, result.shuffledDeckA())),
				new PlayerSnapshot(Side.B, opponentId, opponentName, new DeckSnapshot(true, result.shuffledDeckB())));
		return mapper.writeValueAsString(
				new Snapshot(null, new Rules(played.winThreshold(), played.endBonusPerCard()), players));
	}

	public Snapshot snapshot(String decksSnapshot) {
		return mapper.readValue(decksSnapshot, Snapshot.class);
	}

	public String turnLog(List<MatchEvent> events) {
		return mapper.writeValueAsString(events);
	}

	/** {@code endReason} of the last event of the log (BATTLE_FINISHED). */
	public String endReason(String turnLog) {
		JsonNode events = mapper.readTree(turnLog);
		return events.get(events.size() - 1).get("endReason").asString();
	}

	/**
	 * The Battle Viewer document of docs/battle-viewer.schema.json. The display names are the current usernames,
	 * so a deleted account is shown anonymised in old replays too.
	 */
	public Map<String, Object> replay(Battle battle, String turnLog) {
		Snapshot snapshot = snapshot(battle.getDecksSnapshot());
		JsonNode events = mapper.readTree(turnLog);
		JsonNode last = events.get(events.size() - 1);

		List<PlayerSnapshot> players = snapshot.players().stream()
				.map(p -> new PlayerSnapshot(p.side(), p.playerId(),
						(p.side() == Side.A ? battle.getChallenger() : battle.getOpponent()).getUsername(),
						p.deckSnapshot()))
				.toList();
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("endReason", last.get("endReason"));
		result.put("winnerSide", last.get("winnerSide"));
		result.put("finalEnergyBySide", last.get("finalEnergyBySide"));

		Map<String, Object> document = new LinkedHashMap<>();
		document.put("schemaVersion", SCHEMA_VERSION);
		document.put("battleId", battle.getId());
		document.put("mode", battle.getMode());
		document.put("rules", snapshot.rules());
		document.put("players", players);
		document.put("events", events);
		document.put("result", result);
		return document;
	}
}
