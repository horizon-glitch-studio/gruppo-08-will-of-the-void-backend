package com.horizonglitch.wotv.matchmaking;

import com.horizonglitch.wotv.battle.Battle;
import com.horizonglitch.wotv.battle.BattleMode;
import com.horizonglitch.wotv.battle.BattleRepository;
import com.horizonglitch.wotv.battle.BattleResponses;
import com.horizonglitch.wotv.battle.BattleService;
import com.horizonglitch.wotv.battle.BattleStatus;
import com.horizonglitch.wotv.common.ApiError;
import com.horizonglitch.wotv.common.ApiException;
import com.horizonglitch.wotv.config.AppProperties;
import com.horizonglitch.wotv.deck.Deck;
import com.horizonglitch.wotv.deck.DeckService;
import com.horizonglitch.wotv.player.Player;
import com.horizonglitch.wotv.player.PlayerRepository;
import com.horizonglitch.wotv.player.PlayerService;
import com.horizonglitch.wotv.season.Orbit;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opponent lists, attack limits and shield of rules/game-modes.md (MODE-09 to MODE-22). The list of each mode is
 * saved, so reloading the page does not change it; an attack is accepted only against an opponent of the current
 * list not yet attacked. The player's row is locked in every method, so two requests of the same player cannot
 * both generate a list or attack the same opponent.
 */
@Service
public class MatchmakingService {

	private final OpponentListRepository lists;
	private final OpponentListEntryRepository listEntries;
	private final EntityManager entityManager;
	private final PlayerService players;
	private final PlayerRepository playerRows;
	private final BattleRepository battles;
	private final BattleService battleService;
	private final DeckService decks;
	private final AppProperties.Matchmaking rules;
	private final Clock clock;

	public MatchmakingService(OpponentListRepository lists, OpponentListEntryRepository listEntries,
			EntityManager entityManager, PlayerService players, PlayerRepository playerRows,
			BattleRepository battles, BattleService battleService, DeckService decks, AppProperties properties,
			Clock clock) {
		this.lists = lists;
		this.listEntries = listEntries;
		this.entityManager = entityManager;
		this.players = players;
		this.playerRows = playerRows;
		this.battles = battles;
		this.battleService = battleService;
		this.decks = decks;
		this.rules = properties.matchmaking();
		this.clock = clock;
	}

	/** The saved list of the mode; it is generated the first time it is asked for (MODE-09). */
	@Transactional
	public MatchmakingResponses.OpponentListView current(long playerId, BattleMode mode) {
		requireListMode(mode);
		Player player = players.lockById(playerId);
		OpponentList list = lists.findById(new OpponentListId(playerId, mode)).orElse(null);
		if (list == null) {
			list = new OpponentList();
			list.setId(new OpponentListId(playerId, mode));
			list.setPlayer(player);
			fill(list, clock.instant());
			entityManager.persist(list);
		}
		return view(list);
	}

	/** A new list, at most once every {@code refreshCooldown} (MODE-11). */
	@Transactional
	public MatchmakingResponses.OpponentListView refresh(long playerId, BattleMode mode) {
		requireListMode(mode);
		Player player = players.lockById(playerId);
		Instant now = clock.instant();
		OpponentList list = lists.findById(new OpponentListId(playerId, mode)).orElse(null);
		boolean isNew = list == null;
		if (isNew) {
			list = new OpponentList();
			list.setId(new OpponentListId(playerId, mode));
			list.setPlayer(player);
		} else {
			Instant available = refreshAvailableAt(list);
			if (available.isAfter(now)) {
				throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "MATCHMAKING_COOLDOWN",
						"The list was refreshed a moment ago.", Map.of("refreshAvailableAt", available.toString()));
			}
			// the old entries are deleted before the new ones take the same positions
			list.getEntries().clear();
			lists.flush();
		}
		fill(list, now);
		list.setLastRefreshAt(now);
		if (isNew) {
			entityManager.persist(list);
		}
		return view(list);
	}

	/**
	 * Attacks an opponent of the current list (MODE-10). RANKED is always instant and needs a valid defense deck
	 * from both Summoners (MODE-08); QUICK becomes a challenge when the opponent has none (MODE-14). The entry is
	 * linked to the battle only when it is PLAYED, so a rejected or expired challenge leaves the opponent available.
	 */
	@Transactional
	public BattleResponses.BattleView attack(long playerId, long opponentId, MatchmakingRequests.Start request) {
		BattleMode mode = request.mode();
		requireListMode(mode);
		if (opponentId == playerId) {
			throw notInList();
		}
		// the opponent must be in the list before their row is locked: nobody can lock arbitrary players
		if (!listEntries.isInList(playerId, mode, opponentId)) {
			throw notInList();
		}
		BattleService.Locked locked = battleService.lockBoth(playerId, opponentId);
		// read after the locks: a refresh or an acceptance may have changed the entry meanwhile
		OpponentListEntry entry = listEntries.findEntry(playerId, mode, opponentId)
				.orElseThrow(MatchmakingService::notInList);
		if (entry.getBattle() != null) {
			throw new ApiException(HttpStatus.CONFLICT, "OPPONENT_ALREADY_ATTACKED",
					"This opponent was already attacked: refresh the list.");
		}
		Deck deck = decks.requireValid(playerId, request.deckId());
		Instant now = clock.instant();
		requireAttackAllowed(playerId, opponentId, mode, now);

		Battle battle;
		if (mode == BattleMode.RANKED) {
			if (decks.validDefenseDeck(locked.challenger()).isEmpty()) {
				throw new ApiException(HttpStatus.CONFLICT, "DEFENSE_DECK_REQUIRED",
						"A valid defense deck is required to play Ranked.");
			}
			if (playerRows.isShielded(opponentId, now)) {
				throw new ApiException(HttpStatus.CONFLICT, "OPPONENT_SHIELDED", "This opponent has an active shield.");
			}
			Deck defense = decks.validDefenseDeck(locked.opponent()).orElseThrow(() -> new ApiException(
					HttpStatus.CONFLICT, "OPPONENT_NOT_AVAILABLE", "This opponent has no valid defense deck any more."));
			battle = battleService.playInstant(locked, mode, deck, defense, null);
		} else {
			battle = battleService.startInstantOrChallenge(locked, mode, deck, null);
		}
		if (battle.getStatus() == BattleStatus.PLAYED) {
			entry.setBattle(battle);
		}
		return battleService.view(battle);
	}

	// ------------------------------------------------------------------ internals

	/** MODE-16 and MODE-17: attacks on the same opponent in the last {@code attackWindow}. */
	private void requireAttackAllowed(long playerId, long opponentId, BattleMode mode, Instant now) {
		int limit = mode == BattleMode.RANKED ? rules.rankedAttacksPerOpponent() : rules.quickAttacksPerOpponent();
		long done = battles.countAttacks(playerId, opponentId, mode.name(), now.minus(rules.attackWindow()));
		if (done >= limit) {
			throw new ApiException(HttpStatus.CONFLICT, "ATTACK_LIMIT_REACHED",
					"This opponent was attacked too many times in the last hours.", Map.of("limit", limit));
		}
	}

	private void fill(OpponentList list, Instant now) {
		long playerId = list.getId().getPlayerId();
		List<PlayerRepository.Candidate> candidates = list.getId().getMode() == BattleMode.RANKED
				? rankedCandidates(playerId, now)
				: playerRows.findQuickCandidates(playerId, now.minus(rules.quickActive()), rules.listSize());
		list.setGeneratedAt(now);
		short position = 1;
		for (PlayerRepository.Candidate candidate : candidates) {
			OpponentListEntry entry = new OpponentListEntry(list, position++);
			entry.setOpponent(playerRows.getReferenceById(candidate.getId()));
			entry.setTrophiesAtGeneration(candidate.getTrophies());
			list.getEntries().add(entry);
		}
	}

	/**
	 * MODE-12 and MODE-13: opponents within the range of the player's Orbit, never below 0; while the list is not
	 * full the range is doubled, until it covers every Summoner.
	 */
	private List<PlayerRepository.Candidate> rankedCandidates(long playerId, Instant now) {
		int trophies = playerRows.trophiesOf(playerId);
		int highest = playerRows.maxTrophies();
		int range = rules.trophyRanges().get(Orbit.fromTrophies(trophies).ordinal());
		while (true) {
			int min = Math.max(0, trophies - range);
			int max = (int) Math.min(Integer.MAX_VALUE, (long) trophies + range);
			List<PlayerRepository.Candidate> found = playerRows.findRankedCandidates(playerId, now, min, max,
					rules.listSize());
			if (found.size() >= rules.listSize() || (min == 0 && max >= highest)) {
				return found;
			}
			range = (int) Math.min(Integer.MAX_VALUE, (long) range * 2);
		}
	}

	private Instant refreshAvailableAt(OpponentList list) {
		return list.getLastRefreshAt() == null ? list.getGeneratedAt()
				: list.getLastRefreshAt().plus(rules.refreshCooldown());
	}

	private MatchmakingResponses.OpponentListView view(OpponentList list) {
		List<MatchmakingResponses.OpponentView> opponents = list.getEntries().stream().map(entry -> {
			Player opponent = entry.getOpponent();
			Long battleId = Optional.ofNullable(entry.getBattle()).map(Battle::getId).orElse(null);
			return new MatchmakingResponses.OpponentView(entry.getId().getPosition(),
					new BattleResponses.PlayerRef(opponent.getId(), opponent.getUsername()),
					entry.getTrophiesAtGeneration(), Orbit.fromTrophies(entry.getTrophiesAtGeneration()),
					battleId != null, battleId);
		}).toList();
		return new MatchmakingResponses.OpponentListView(list.getId().getMode(), list.getGeneratedAt(),
				refreshAvailableAt(list), opponents);
	}

	private static void requireListMode(BattleMode mode) {
		if (mode != BattleMode.QUICK && mode != BattleMode.RANKED) {
			throw ApiException.validation(List.of(new ApiError.FieldErrorItem("mode", "MODE_INVALID")));
		}
	}

	private static ApiException notInList() {
		return new ApiException(HttpStatus.CONFLICT, "OPPONENT_NOT_IN_LIST",
				"This opponent is not in the current list.");
	}
}
