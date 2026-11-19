package com.horizonglitch.wotv.battle;

import com.horizonglitch.wotv.common.ApiError;
import com.horizonglitch.wotv.common.ApiException;
import com.horizonglitch.wotv.common.PageResponse;
import com.horizonglitch.wotv.common.Paging;
import com.horizonglitch.wotv.config.AppProperties;
import com.horizonglitch.wotv.deck.Deck;
import com.horizonglitch.wotv.deck.DeckService;
import com.horizonglitch.wotv.engine.CardInput;
import com.horizonglitch.wotv.engine.MatchResult;
import com.horizonglitch.wotv.engine.Winner;
import com.horizonglitch.wotv.matchmaking.OpponentListEntryRepository;
import com.horizonglitch.wotv.notification.NotificationService;
import com.horizonglitch.wotv.notification.NotificationType;
import com.horizonglitch.wotv.player.Player;
import com.horizonglitch.wotv.player.PlayerRepository;
import com.horizonglitch.wotv.player.PlayerService;
import com.horizonglitch.wotv.season.Orbit;
import com.horizonglitch.wotv.season.Season;
import com.horizonglitch.wotv.season.SeasonService;
import com.horizonglitch.wotv.shard.ShardService;
import com.horizonglitch.wotv.social.FriendshipRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Battles of rules/game-modes.md and rules/match.md. A battle is played at once against the opponent's valid
 * defense deck (MODE-03); otherwise it becomes a challenge the opponent accepts with one of their decks (MODE-04,
 * MODE-05). The match is simulated by the engine from the two decks and a seed generated here: the client never
 * sends seed, outcome, Energy or trophies.
 *
 * <p>Every method that plays a battle or creates a challenge holds the lock on both players' rows, taken in id
 * order, so trophies, shield, attack limits and the single pending challenge per pair are checked one request
 * at a time. Accept, reject and cancel return an {@link Outcome}: a challenge that expired is saved as such, and
 * the controller turns the outcome into an error only after the transaction has committed.
 */
@Service
public class BattleService {

	private final BattleRepository battles;
	private final BattleLogRepository logs;
	private final OpponentListEntryRepository listEntries;
	private final PlayerService players;
	private final SeasonService seasons;
	private final ShardService shards;
	private final PlayerRepository playerRows;
	private final DeckService decks;
	private final FriendshipRepository friendships;
	private final NotificationService notifications;
	private final MatchSimulator simulator;
	private final BattleJson json;
	private final RandomGenerator random;
	private final AppProperties.Battles rules;
	private final AppProperties.Matchmaking matchmaking;
	private final Clock clock;

	public BattleService(BattleRepository battles, BattleLogRepository logs, OpponentListEntryRepository listEntries,
			PlayerService players, SeasonService seasons, ShardService shards, PlayerRepository playerRows, DeckService decks,
			FriendshipRepository friendships,
			NotificationService notifications, MatchSimulator simulator, BattleJson json, RandomGenerator random,
			AppProperties properties, Clock clock) {
		this.battles = battles;
		this.logs = logs;
		this.listEntries = listEntries;
		this.players = players;
		this.seasons = seasons;
		this.shards = shards;
		this.playerRows = playerRows;
		this.decks = decks;
		this.friendships = friendships;
		this.notifications = notifications;
		this.simulator = simulator;
		this.json = json;
		this.random = random;
		this.rules = properties.battles();
		this.matchmaking = properties.matchmaking();
		this.clock = clock;
	}

	/** The result of an action on a challenge: the battle, and the error to return when the action did not happen. */
	public record Outcome(BattleResponses.BattleView battle, ApiException error) {

		static Outcome ok(BattleResponses.BattleView battle) {
			return new Outcome(battle, null);
		}
	}

	/** The two players with their rows locked, challenger first. */
	public record Locked(Player challenger, Player opponent) {
	}

	// ------------------------------------------------------------------ starting a battle

	/** A Friend match (MODE-03, MODE-04): only against a friend, instant or challenge. */
	@Transactional
	public BattleResponses.BattleView challengeFriend(long playerId, BattleRequests.Friend request) {
		long opponentId = playerRows.findActiveIdByUsername(request.opponentUsername().trim())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAYER_NOT_FOUND", "The player does not exist."));
		if (opponentId == playerId) {
			throw ApiException.validation(List.of(new ApiError.FieldErrorItem("opponentUsername", "BATTLE_WITH_SELF")));
		}
		Locked locked = lockBoth(playerId, opponentId);
		requireFriends(playerId, opponentId);
		Deck deck = decks.requireValid(playerId, request.deckId());
		return view(startInstantOrChallenge(locked, BattleMode.FRIEND, deck, null));
	}

	/**
	 * A rematch of a PLAYED Friend or Quick battle (MODE-23, MODE-24): a new battle in the same mode against the
	 * same Summoner, asked by either of the two, instant or challenge like any other.
	 */
	@Transactional
	public BattleResponses.BattleView rematch(long playerId, long battleId, BattleRequests.DeckChoice request) {
		Battle original = battles.findById(battleId).orElseThrow(BattleService::notFound);
		requireParty(original, playerId);
		if (original.getStatus() != BattleStatus.PLAYED) {
			throw notPlayed();
		}
		if (original.getMode() == BattleMode.RANKED) {
			throw new ApiException(HttpStatus.CONFLICT, "REMATCH_NOT_ALLOWED", "A Ranked battle has no rematch.");
		}
		long challengerId = original.getChallenger().getId();
		long otherId = challengerId == playerId ? original.getOpponent().getId() : challengerId;
		Locked locked = lockBoth(playerId, otherId);
		if (original.getMode() == BattleMode.FRIEND) {
			requireFriends(playerId, otherId);
		}
		Deck deck = decks.requireValid(playerId, request.deckId());
		return view(startInstantOrChallenge(locked, original.getMode(), deck, original));
	}

	/**
	 * Locks both rows in id order, so two requests about the same pair never wait on each other. A deleted
	 * account is refused with PLAYER_NOT_FOUND.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Locked lockBoth(long challengerId, long opponentId) {
		Player first = players.lockById(Math.min(challengerId, opponentId));
		Player second = players.lockById(Math.max(challengerId, opponentId));
		return challengerId < opponentId ? new Locked(first, second) : new Locked(second, first);
	}

	/** Played at once against the opponent's valid defense deck; a challenge without one (MODE-03, MODE-04). */
	@Transactional(propagation = Propagation.MANDATORY)
	public Battle startInstantOrChallenge(Locked locked, BattleMode mode, Deck challengerDeck, Battle rematchOf) {
		Optional<Deck> defense = decks.validDefenseDeck(locked.opponent());
		if (defense.isPresent()) {
			return playInstant(locked, mode, challengerDeck, defense.get(), rematchOf);
		}
		return createChallenge(locked, mode, challengerDeck, rematchOf);
	}

	/** Plays a new battle now. The caller holds both locks and has checked the decks. */
	@Transactional(propagation = Propagation.MANDATORY)
	public Battle playInstant(Locked locked, BattleMode mode, Deck challengerDeck, Deck opponentDeck, Battle rematchOf) {
		Battle battle = new Battle();
		battle.setMode(mode);
		battle.setChallenger(locked.challenger());
		battle.setOpponent(locked.opponent());
		battle.setChallengerDeck(challengerDeck);
		battle.setRematchOf(rematchOf);
		play(battle, locked, MatchSimulator.cardsOf(challengerDeck), opponentDeck);
		notifications.notifyBattle(locked.opponent(), NotificationType.ATTACK_RECEIVED, battle);
		return battle;
	}

	/**
	 * A challenge the opponent can accept until it expires (MODE-06). The challenger's deck is saved as it is now
	 * (MODE-04); at most one challenge can be pending between two Summoners (MODE-07).
	 */
	private Battle createChallenge(Locked locked, BattleMode mode, Deck challengerDeck, Battle rematchOf) {
		Instant now = clock.instant();
		long challengerId = locked.challenger().getId();
		long opponentId = locked.opponent().getId();
		battles.lockPendingBetween(challengerId, opponentId).ifPresent(pending -> {
			if (pending.getExpiresAt().isAfter(now)) {
				throw alreadyPending();
			}
			expire(pending);
			battles.flush();
		});

		Battle battle = new Battle();
		battle.setMode(mode);
		battle.setStatus(BattleStatus.PENDING);
		battle.setChallenger(locked.challenger());
		battle.setOpponent(locked.opponent());
		battle.setChallengerDeck(challengerDeck);
		battle.setRematchOf(rematchOf);
		battle.setDecksSnapshot(json.pendingSnapshot(MatchSimulator.cardsOf(challengerDeck)));
		battle.setExpiresAt(now.plus(rules.challengeExpiry()));
		try {
			battles.saveAndFlush(battle);
		} catch (DataIntegrityViolationException e) {
			if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("uq_battle_pending_pair")) {
				throw alreadyPending();
			}
			throw e;
		}
		notifications.notifyBattle(locked.opponent(), NotificationType.CHALLENGE_RECEIVED, battle);
		return battle;
	}

	// ------------------------------------------------------------------ challenges

	/** Accepts as the challenged Summoner, with one of their decks: the match is played at once (MODE-05). */
	@Transactional
	public Outcome accept(long playerId, long battleId, BattleRequests.DeckChoice request) {
		// players first, then the battle: the same order as a new challenge between the two, so they never deadlock
		BattleRepository.Parties parties = battles.findParties(battleId).orElseThrow(BattleService::notFound);
		if (parties.getOpponentId() != playerId) {
			throw forbidden(parties.getChallengerId() == playerId ? "Only the challenged player can accept the challenge."
					: "Only the two players of the battle can see it.");
		}
		Locked locked = lockBoth(parties.getChallengerId(), playerId);
		Battle battle = battles.lockById(battleId).orElseThrow(BattleService::notFound);
		Outcome notPending = checkPending(battle);
		if (notPending != null) {
			return notPending;
		}
		Deck deck = decks.requireValid(playerId, request.deckId());
		List<CardInput> challengerDeck = json.snapshot(battle.getDecksSnapshot()).challengerDeck();
		play(battle, locked, challengerDeck, deck);
		if (battle.getMode() == BattleMode.QUICK && battle.getRematchOf() == null) {
			// the opponent stays attackable until the battle is PLAYED; a list refreshed meanwhile has no entry
			listEntries.findEntry(locked.challenger().getId(), BattleMode.QUICK, playerId)
					.filter(entry -> entry.getBattle() == null)
					.ifPresent(entry -> entry.setBattle(battle));
		}
		notifications.notifyBattle(locked.challenger(), NotificationType.CHALLENGE_ACCEPTED, battle);
		return Outcome.ok(view(battle));
	}

	@Transactional
	public Outcome reject(long playerId, long battleId) {
		Battle battle = battles.lockById(battleId).orElseThrow(BattleService::notFound);
		requireRole(battle, playerId, false, "Only the challenged player can reject the challenge.");
		Outcome notPending = checkPending(battle);
		if (notPending != null) {
			return notPending;
		}
		battle.setStatus(BattleStatus.REJECTED);
		notifications.notifyBattle(battle.getChallenger(), NotificationType.CHALLENGE_REJECTED, battle);
		return Outcome.ok(view(battle));
	}

	@Transactional
	public Outcome cancel(long playerId, long battleId) {
		Battle battle = battles.lockById(battleId).orElseThrow(BattleService::notFound);
		requireRole(battle, playerId, true, "Only the challenger can cancel the challenge.");
		Outcome notPending = checkPending(battle);
		if (notPending != null) {
			return notPending;
		}
		battle.setStatus(BattleStatus.CANCELLED);
		notifications.notifyBattle(battle.getOpponent(), NotificationType.CHALLENGE_CANCELLED, battle);
		return Outcome.ok(view(battle));
	}

	/** Expires one challenge if it is still pending and past its deadline. Used by {@link BattleExpiryJob}. */
	@Transactional
	public void expireIfDue(long battleId) {
		battles.lockById(battleId).ifPresent(battle -> {
			if (battle.getStatus() == BattleStatus.PENDING && !battle.getExpiresAt().isAfter(clock.instant())) {
				expire(battle);
			}
		});
	}

	/** Cancels the pending challenges of a player whose account is being deleted. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void cancelAllPending(Player player) {
		for (Battle battle : battles.findPendingOf(player.getId())) {
			battle.setStatus(BattleStatus.CANCELLED);
			boolean challenger = battle.getChallenger().getId().equals(player.getId());
			notifications.notifyBattle(challenger ? battle.getOpponent() : battle.getChallenger(),
					NotificationType.CHALLENGE_CANCELLED, battle);
		}
	}

	/** Deletes the logs older than the replay retention (MATCH-18). Used by {@link BattleExpiryJob}. */
	@Transactional
	public int deleteOldReplays() {
		return logs.deleteOfBattlesPlayedBefore(clock.instant().minus(rules.replayRetention()));
	}

	// ------------------------------------------------------------------ reading

	@Transactional(readOnly = true)
	public BattleResponses.BattleView get(long playerId, long battleId) {
		Battle battle = battles.findById(battleId).orElseThrow(BattleService::notFound);
		requireParty(battle, playerId);
		return view(battle);
	}

	@Transactional(readOnly = true)
	public BattleResponses.ResultView result(long playerId, long battleId) {
		Battle battle = playedBattleOf(playerId, battleId);
		String endReason = logs.findById(battleId).map(log -> json.endReason(log.getTurnLog())).orElse(null);
		int winThreshold = json.snapshot(battle.getDecksSnapshot()).rules().energyWinThreshold();
		return new BattleResponses.ResultView(battle.getId(), battle.getMode(), battle.getOutcome(), endReason,
				winThreshold,
				new BattleResponses.SideResult(ref(battle.getChallenger()), battle.getChallengerEnergy(),
						battle.getChallengerTrophiesDelta()),
				new BattleResponses.SideResult(ref(battle.getOpponent()), battle.getOpponentEnergy(),
						battle.getOpponentTrophiesDelta()),
				battle.getPlayedAt());
	}

	/**
	 * The Battle Viewer document of docs/battle-viewer.schema.json, for the two Summoners of the battle and only
	 * while the replay is kept (MATCH-18).
	 */
	@Transactional(readOnly = true)
	public Map<String, Object> replay(long playerId, long battleId) {
		Battle battle = playedBattleOf(playerId, battleId);
		boolean kept = battle.getPlayedAt().plus(rules.replayRetention()).isAfter(clock.instant());
		BattleLog log = kept ? logs.findById(battleId).orElse(null) : null;
		if (log == null) {
			throw new ApiException(HttpStatus.GONE, "REPLAY_EXPIRED", "The replay of this battle is no longer available.");
		}
		return json.replay(battle, log.getTurnLog());
	}

	/**
	 * The battles and challenges of the player, newest first. {@code result} is WIN, LOSS or DRAW from the
	 * player's side; {@code from} and {@code to} filter on the creation time.
	 */
	@Transactional(readOnly = true)
	public PageResponse<BattleResponses.BattleView> history(long playerId, BattleMode mode, BattleStatus status,
			String result, Instant from, Instant to, Integer page, Integer size) {
		Specification<Battle> spec = (root, query, cb) -> cb.or(cb.equal(root.get("challenger").get("id"), playerId),
				cb.equal(root.get("opponent").get("id"), playerId));
		if (mode != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("mode"), mode));
		}
		if (status != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
		}
		if (result != null) {
			spec = spec.and(resultOf(playerId, result));
		}
		if (from != null) {
			spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
		}
		if (to != null) {
			spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), to));
		}
		Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
		return PageResponse.of(battles.findAll(spec, Paging.of(page, size, newestFirst)), this::view);
	}

	public BattleResponses.BattleView view(Battle battle) {
		return new BattleResponses.BattleView(battle.getId(), battle.getMode(), battle.getStatus(),
				ref(battle.getChallenger()), ref(battle.getOpponent()), battle.getOutcome(),
				battle.getChallengerEnergy(), battle.getOpponentEnergy(), battle.getChallengerTrophiesDelta(),
				battle.getOpponentTrophiesDelta(), battle.getCreatedAt(), battle.getExpiresAt(), battle.getPlayedAt(),
				battle.getRematchOf() == null ? null : battle.getRematchOf().getId());
	}

	// ------------------------------------------------------------------ internals

	/**
	 * Simulates the match and saves everything needed to replay it: seed, decks in draw order, rules and log.
	 * Side A is the challenger. In RANKED it also moves the trophies and handles the shield.
	 */
	private void play(Battle battle, Locked locked, List<CardInput> challengerDeck, Deck opponentDeck) {
		Instant now = clock.instant();
		Player challenger = locked.challenger();
		Player opponent = locked.opponent();
		long seed = random.nextLong();
		MatchSimulator.Played played = simulator.play(challengerDeck, MatchSimulator.cardsOf(opponentDeck),
				battle.getMode(), seed);
		MatchResult result = played.result();

		battle.setStatus(BattleStatus.PLAYED);
		battle.setOpponentDeck(opponentDeck);
		battle.setSeed(seed);
		battle.setOutcome(outcomeOf(result.winner()));
		battle.setChallengerEnergy(result.finalEnergy().A());
		battle.setOpponentEnergy(result.finalEnergy().B());
		battle.setPlayedAt(now);
		battle.setDecksSnapshot(json.playedSnapshot(played, challenger.getId(), challenger.getUsername(),
				opponent.getId(), opponent.getUsername()));
		if (battle.getMode() == BattleMode.RANKED) {
			moveTrophies(battle, challenger, opponent, seasons.currentAt(now).orElse(null));
		}
		battles.saveAndFlush(battle);
		shards.grantInviteBonusIfEligible(challenger);
		shards.grantInviteBonusIfEligible(opponent);

		BattleLog log = new BattleLog();
		log.setBattle(battle);
		log.setTurnLog(json.turnLog(result.events()));
		logs.save(log);

		if (battle.getMode() == BattleMode.RANKED) {
			updateShields(challenger, opponent, now);
		}
	}

	/**
	 * RANK-04 and RANK-05: Elo on the current trophies, what one gains the other loses, never below 0. The deltas
	 * saved are the trophies actually gained or lost.
	 */
	private void moveTrophies(Battle battle, Player challenger, Player opponent, Season season) {
		int challengerTrophies = playerRows.trophiesOf(challenger.getId());
		int opponentTrophies = playerRows.trophiesOf(opponent.getId());
		double result = switch (battle.getOutcome()) {
			case CHALLENGER_WIN -> Elo.WIN;
			case DRAW -> Elo.DRAW;
			case OPPONENT_WIN -> Elo.DEFEAT;
		};
		int delta = Elo.delta(challengerTrophies, opponentTrophies, result, rules.eloK());
		int challengerDelta = Math.max(delta, -challengerTrophies);
		int opponentDelta = Math.max(-delta, -opponentTrophies);
		playerRows.addTrophies(challenger.getId(), challengerDelta);
		playerRows.addTrophies(opponent.getId(), opponentDelta);
		battle.setChallengerTrophiesDelta(challengerDelta);
		battle.setOpponentTrophiesDelta(opponentDelta);
		if (season != null) {
			grantOrbitRewardOnPromotion(challenger, challengerTrophies, challengerDelta, season);
			grantOrbitRewardOnPromotion(opponent, opponentTrophies, opponentDelta, season);
		}
	}

	/** RANK-11: reward an upward Orbit crossing; the transaction uniqueness key suppresses repeat climbs. */
	private void grantOrbitRewardOnPromotion(Player player, int trophiesBefore, int delta, Season season) {
		if (delta <= 0) {
			return;
		}
		Orbit before = Orbit.fromTrophies(trophiesBefore);
		Orbit after = Orbit.fromTrophies(trophiesBefore + delta);
		if (after.ordinal() > before.ordinal()) {
			shards.grantOrbitRewardIfEligible(player, season, after);
		}
	}

	/** MODE-21: attacking in Ranked ends the attacker's shield. MODE-19: enough attacks received start one. */
	private void updateShields(Player challenger, Player opponent, Instant now) {
		playerRows.endShield(challenger.getId(), now);
		long received = battles.countRankedAttacksReceived(opponent.getId(), now.minus(matchmaking.shieldWindow()));
		if (received >= matchmaking.shieldTriggerAttacks()) {
			playerRows.shieldUntil(opponent.getId(), now.plus(matchmaking.shieldDuration()));
		}
	}

	/**
	 * Null when the challenge can still be acted on. A challenge past its deadline is expired here, even before
	 * the job runs, and the action is refused: BATTLE_EXPIRED for an expired one, BATTLE_NOT_PENDING otherwise.
	 */
	private Outcome checkPending(Battle battle) {
		if (battle.getStatus() == BattleStatus.PENDING && !battle.getExpiresAt().isAfter(clock.instant())) {
			expire(battle);
		}
		if (battle.getStatus() == BattleStatus.PENDING) {
			return null;
		}
		if (battle.getStatus() == BattleStatus.EXPIRED) {
			return new Outcome(view(battle),
					new ApiException(HttpStatus.CONFLICT, "BATTLE_EXPIRED", "The challenge has expired."));
		}
		return new Outcome(view(battle), new ApiException(HttpStatus.CONFLICT, "BATTLE_NOT_PENDING",
				"The challenge is no longer pending.", Map.of("status", battle.getStatus().name())));
	}

	private void expire(Battle battle) {
		battle.setStatus(BattleStatus.EXPIRED);
		notifications.notifyBattle(battle.getChallenger(), NotificationType.CHALLENGE_EXPIRED, battle);
		notifications.notifyBattle(battle.getOpponent(), NotificationType.CHALLENGE_EXPIRED, battle);
	}

	private Battle playedBattleOf(long playerId, long battleId) {
		Battle battle = battles.findById(battleId).orElseThrow(BattleService::notFound);
		requireParty(battle, playerId);
		if (battle.getStatus() != BattleStatus.PLAYED) {
			throw notPlayed();
		}
		return battle;
	}

	private void requireFriends(long playerId, long otherId) {
		if (!friendships.areFriends(playerId, otherId)) {
			throw new ApiException(HttpStatus.CONFLICT, "NOT_FRIENDS", "A Friend match is only against a friend.");
		}
	}

	private static void requireParty(Battle battle, long playerId) {
		if (!isParty(battle, playerId)) {
			throw forbidden("Only the two players of the battle can see it.");
		}
	}

	/** The action belongs to the challenger or to the challenged player; anyone else does not even see the battle. */
	private static void requireRole(Battle battle, long playerId, boolean challenger, String message) {
		requireParty(battle, playerId);
		Player expected = challenger ? battle.getChallenger() : battle.getOpponent();
		if (!expected.getId().equals(playerId)) {
			throw forbidden(message);
		}
	}

	private static boolean isParty(Battle battle, long playerId) {
		return battle.getChallenger().getId().equals(playerId) || battle.getOpponent().getId().equals(playerId);
	}

	private static Specification<Battle> resultOf(long playerId, String result) {
		return switch (result) {
			case "WIN" -> wonBy(playerId, BattleOutcome.CHALLENGER_WIN, BattleOutcome.OPPONENT_WIN);
			case "LOSS" -> wonBy(playerId, BattleOutcome.OPPONENT_WIN, BattleOutcome.CHALLENGER_WIN);
			case "DRAW" -> (root, query, cb) -> cb.equal(root.get("outcome"), BattleOutcome.DRAW);
			default -> throw ApiException.validation(List.of(new ApiError.FieldErrorItem("result", "RESULT_INVALID")));
		};
	}

	private static Specification<Battle> wonBy(long playerId, BattleOutcome asChallenger, BattleOutcome asOpponent) {
		return (root, query, cb) -> cb.or(
				cb.and(cb.equal(root.get("challenger").get("id"), playerId), cb.equal(root.get("outcome"), asChallenger)),
				cb.and(cb.equal(root.get("opponent").get("id"), playerId), cb.equal(root.get("outcome"), asOpponent)));
	}

	private static BattleOutcome outcomeOf(Winner winner) {
		return switch (winner) {
			case A -> BattleOutcome.CHALLENGER_WIN;
			case B -> BattleOutcome.OPPONENT_WIN;
			case DRAW -> BattleOutcome.DRAW;
		};
	}

	private static BattleResponses.PlayerRef ref(Player player) {
		return new BattleResponses.PlayerRef(player.getId(), player.getUsername());
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "BATTLE_NOT_FOUND", "The battle does not exist.");
	}

	private static ApiException forbidden(String message) {
		return new ApiException(HttpStatus.FORBIDDEN, "BATTLE_FORBIDDEN", message);
	}

	private static ApiException notPlayed() {
		return new ApiException(HttpStatus.CONFLICT, "BATTLE_NOT_PLAYED", "The battle has not been played.");
	}

	private static ApiException alreadyPending() {
		return new ApiException(HttpStatus.CONFLICT, "BATTLE_ALREADY_PENDING",
				"A pending challenge between these players already exists.");
	}
}
