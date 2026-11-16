package com.horizonglitch.wotv.battle;

import com.horizonglitch.wotv.card.Card;
import com.horizonglitch.wotv.card.FactionAdvantageRepository;
import com.horizonglitch.wotv.config.AppProperties;
import com.horizonglitch.wotv.deck.Deck;
import com.horizonglitch.wotv.deck.DeckCard;
import com.horizonglitch.wotv.engine.AdvantageTable;
import com.horizonglitch.wotv.engine.CardInput;
import com.horizonglitch.wotv.engine.MatchEngine;
import com.horizonglitch.wotv.engine.MatchMode;
import com.horizonglitch.wotv.engine.MatchParameters;
import com.horizonglitch.wotv.engine.MatchRequest;
import com.horizonglitch.wotv.engine.MatchResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Prepares the input of the {@code engine} module and runs it. The match parameters come from the settings
 * (app.battles.*) and the faction advantages from the {@code faction_advantage} table, so a value marked [TEST]
 * changes without touching the engine.
 */
@Component
public class MatchSimulator {

	private final MatchEngine engine = new MatchEngine();
	private final FactionAdvantageRepository advantages;
	private final AppProperties.Battles settings;

	public MatchSimulator(FactionAdvantageRepository advantages, AppProperties properties) {
		this.advantages = advantages;
		this.settings = properties.battles();
	}

	/** The result of a match with the rules it was played with, saved in the replay. */
	public record Played(MatchResult result, int winThreshold, int endBonusPerCard) {
	}

	/** Side A is the challenger, side B the opponent. The same decks and seed always give the same match. */
	public Played play(List<CardInput> challengerDeck, List<CardInput> opponentDeck, BattleMode mode, long seed) {
		MatchParameters parameters = parameters();
		MatchMode matchMode = MatchMode.valueOf(mode.name());
		MatchResult result = engine.play(new MatchRequest(challengerDeck, opponentDeck, matchMode, seed, parameters));
		return new Played(result, parameters.winThreshold(matchMode), parameters.endBonusPerCard());
	}

	private MatchParameters parameters() {
		List<AdvantageTable.Advantage> table = advantages.findAllWithFactions().stream()
				.map(a -> new AdvantageTable.Advantage(a.getDominantFaction().getName(),
						a.getDominatedFaction().getName(), a.getValue()))
				.toList();
		return new MatchParameters(settings.winThresholdFriend(), settings.winThresholdRanked(),
				settings.winThresholdQuick(), settings.endBonusPerCard(), AdvantageTable.of(table));
	}

	/** The deck as the engine wants it: one entry per copy, ordered by card id so the input order is stable. */
	public static List<CardInput> cardsOf(Deck deck) {
		List<CardInput> cards = new ArrayList<>();
		deck.getCards().stream().sorted(Comparator.comparing((DeckCard dc) -> dc.getCard().getId())).forEach(dc -> {
			Card card = dc.getCard();
			for (int i = 0; i < dc.getQuantity(); i++) {
				cards.add(new CardInput(card.getId(), card.getName(), card.getFaction().getName(),
						card.getRarity().getName(), card.getAttack(), card.getDefense()));
			}
		});
		return cards;
	}
}
