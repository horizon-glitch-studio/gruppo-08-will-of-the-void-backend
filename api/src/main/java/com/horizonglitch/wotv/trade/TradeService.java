package com.horizonglitch.wotv.trade;

import com.horizonglitch.wotv.card.Card;
import com.horizonglitch.wotv.card.CatalogResponses;
import com.horizonglitch.wotv.card.CatalogService;
import com.horizonglitch.wotv.collection.CollectionService;
import com.horizonglitch.wotv.common.ApiError;
import com.horizonglitch.wotv.common.ApiException;
import com.horizonglitch.wotv.common.PageResponse;
import com.horizonglitch.wotv.common.Paging;
import com.horizonglitch.wotv.config.AppProperties;
import com.horizonglitch.wotv.deck.DeckService;
import com.horizonglitch.wotv.notification.NotificationService;
import com.horizonglitch.wotv.notification.NotificationType;
import com.horizonglitch.wotv.player.Player;
import com.horizonglitch.wotv.player.PlayerService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trades of rules/trades.md. Ownership is checked only at acceptance (TRADE-10), with the trade, both players and
 * the collection rows locked, so cards are never lost or duplicated (TRADE-11).
 *
 * <p>Accept, reject and cancel return an {@link Outcome}: a trade that expired or failed is saved as such, and the
 * controller turns the outcome into an error only after the transaction has committed.
 */
@Service
public class TradeService {

	private final TradeRepository trades;
	private final PlayerService players;
	private final CatalogService catalog;
	private final CollectionService collection;
	private final DeckService decks;
	private final NotificationService notifications;
	private final AppProperties.Trades rules;
	private final Clock clock;

	public TradeService(TradeRepository trades, PlayerService players, CatalogService catalog,
			CollectionService collection, DeckService decks, NotificationService notifications,
			AppProperties properties, Clock clock) {
		this.trades = trades;
		this.players = players;
		this.catalog = catalog;
		this.collection = collection;
		this.decks = decks;
		this.notifications = notifications;
		this.rules = properties.trades();
		this.clock = clock;
	}

	/** The result of an action on a trade: the trade, and the error to return when the action did not happen. */
	public record Outcome(TradeResponses.TradeView trade, ApiException error) {

		static Outcome ok(TradeResponses.TradeView trade) {
			return new Outcome(trade, null);
		}
	}

	@Transactional
	public TradeResponses.TradeView propose(long proposerId, TradeRequests.Propose request) {
		Player proposer = players.getActive(proposerId);
		requireVerifiedEmail(proposer);
		Player recipient = players.findActiveByUsername(request.recipientUsername().trim())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAYER_NOT_FOUND", "The player does not exist."));

		Map<Long, Card> cards = checkCards(proposer, recipient, request);
		Instant now = clock.instant();
		trades.lockPendingBetween(proposer.getId(), recipient.getId()).ifPresent(pending -> {
			if (pending.getExpiresAt().isAfter(now)) {
				throw new ApiException(HttpStatus.CONFLICT, "TRADE_ALREADY_PENDING",
						"A pending trade between these players already exists.");
			}
			expire(pending, now);
			trades.flush();
		});

		Trade trade = new Trade();
		trade.setProposer(proposer);
		trade.setRecipient(recipient);
		trade.setExpiresAt(now.plus(rules.expiry()));
		request.offered().forEach(c -> trade.getCards().add(tradeCard(trade, cards.get(c.cardId()), TradeSide.OFFER, c.quantity())));
		request.requested().forEach(c -> trade.getCards().add(tradeCard(trade, cards.get(c.cardId()), TradeSide.REQUEST, c.quantity())));
		try {
			trades.saveAndFlush(trade);
		} catch (DataIntegrityViolationException e) {
			if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("uq_trade_pending_pair")) {
				throw new ApiException(HttpStatus.CONFLICT, "TRADE_ALREADY_PENDING",
						"A pending trade between these players already exists.");
			}
			throw e;
		}
		notifications.notifyTrade(recipient, NotificationType.TRADE_RECEIVED, trade);
		return view(trade);
	}

	@Transactional(readOnly = true)
	public PageResponse<TradeResponses.TradeView> list(long playerId, TradeStatus status, String direction,
			Integer page, Integer size) {
		Specification<Trade> spec;
		if (direction == null) {
			spec = (root, query, cb) -> cb.or(cb.equal(root.get("proposer").get("id"), playerId),
					cb.equal(root.get("recipient").get("id"), playerId));
		} else if (direction.equals("incoming")) {
			spec = (root, query, cb) -> cb.equal(root.get("recipient").get("id"), playerId);
		} else if (direction.equals("outgoing")) {
			spec = (root, query, cb) -> cb.equal(root.get("proposer").get("id"), playerId);
		} else {
			throw ApiException.validation(List.of(new ApiError.FieldErrorItem("direction", "DIRECTION_INVALID")));
		}
		if (status != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
		}
		Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
		return PageResponse.of(trades.findAll(spec, Paging.of(page, size, newestFirst)), this::view);
	}

	@Transactional(readOnly = true)
	public TradeResponses.TradeView get(long playerId, long tradeId) {
		Trade trade = trades.findById(tradeId).orElseThrow(TradeService::notFound);
		if (!isParty(trade, playerId)) {
			throw forbidden();
		}
		return view(trade);
	}

	/**
	 * Accepts as recipient. Checks, in order: the trade is pending and not expired, the recipient's email is
	 * verified, both players still own the cards (otherwise the trade FAILS), and nobody is left with fewer than
	 * the minimum collection (otherwise the trade stays pending).
	 */
	@Transactional
	public Outcome accept(long playerId, long tradeId) {
		Trade trade = trades.lockById(tradeId).orElseThrow(TradeService::notFound);
		if (!trade.getRecipient().getId().equals(playerId)) {
			throw isParty(trade, playerId) ? forbidden("Only the recipient can accept the trade.") : forbidden();
		}
		Outcome notPending = checkPending(trade);
		if (notPending != null) {
			return notPending;
		}

		long proposerId = trade.getProposer().getId();
		long first = Math.min(proposerId, playerId);
		long second = Math.max(proposerId, playerId);
		Player firstPlayer = players.lockById(first);
		Player secondPlayer = players.lockById(second);
		Player proposer = first == proposerId ? firstPlayer : secondPlayer;
		Player recipient = first == proposerId ? secondPlayer : firstPlayer;
		requireVerifiedEmail(recipient);

		List<Long> cardIds = trade.getCards().stream().map(c -> c.getCard().getId()).distinct().toList();
		Map<Long, Map<Long, Integer>> owned = collection.lockForTrade(proposerId, playerId, cardIds);
		for (TradeCard card : trade.getCards()) {
			long giver = card.getId().getSide() == TradeSide.OFFER ? proposerId : playerId;
			if (owned.get(giver).getOrDefault(card.getCard().getId(), 0) < card.getQuantity()) {
				close(trade, TradeStatus.FAILED);
				notifications.notifyTrade(proposer, NotificationType.TRADE_FAILED, trade);
				return new Outcome(view(trade), new ApiException(HttpStatus.CONFLICT, "TRADE_FAILED",
						"Some cards of the trade are no longer owned: the trade has failed."));
			}
		}

		int given = sum(trade, TradeSide.OFFER);
		int received = sum(trade, TradeSide.REQUEST);
		int proposerAfter = collection.countPermanent(proposerId) - given + received;
		int recipientAfter = collection.countPermanent(playerId) - received + given;
		if (proposerAfter < rules.minCollectionSize() || recipientAfter < rules.minCollectionSize()) {
			throw new ApiException(HttpStatus.CONFLICT, "TRADE_MIN_COLLECTION",
					"After this trade a player would own fewer than " + rules.minCollectionSize() + " cards.");
		}

		for (TradeCard card : trade.getCards()) {
			boolean offer = card.getId().getSide() == TradeSide.OFFER;
			long from = offer ? proposerId : playerId;
			long to = offer ? playerId : proposerId;
			long cardId = card.getCard().getId();
			int before = owned.get(from).get(cardId);
			collection.transfer(from, to, cardId, card.getQuantity(), before);
			owned.get(from).put(cardId, before - card.getQuantity());
		}
		close(trade, TradeStatus.ACCEPTED);
		notifications.notifyTrade(proposer, NotificationType.TRADE_ACCEPTED, trade);
		for (Player party : List.of(proposer, recipient)) {
			if (decks.recheckAll(party)) {
				notifications.notify(party, NotificationType.DECK_INVALID);
			}
		}
		return Outcome.ok(view(trade));
	}

	@Transactional
	public Outcome reject(long playerId, long tradeId) {
		Trade trade = trades.lockById(tradeId).orElseThrow(TradeService::notFound);
		if (!trade.getRecipient().getId().equals(playerId)) {
			throw isParty(trade, playerId) ? forbidden("Only the recipient can reject the trade.") : forbidden();
		}
		Outcome notPending = checkPending(trade);
		if (notPending != null) {
			return notPending;
		}
		close(trade, TradeStatus.REJECTED);
		notifications.notifyTrade(trade.getProposer(), NotificationType.TRADE_REJECTED, trade);
		return Outcome.ok(view(trade));
	}

	@Transactional
	public Outcome cancel(long playerId, long tradeId) {
		Trade trade = trades.lockById(tradeId).orElseThrow(TradeService::notFound);
		if (!trade.getProposer().getId().equals(playerId)) {
			throw isParty(trade, playerId) ? forbidden("Only the proposer can cancel the trade.") : forbidden();
		}
		Outcome notPending = checkPending(trade);
		if (notPending != null) {
			return notPending;
		}
		close(trade, TradeStatus.CANCELLED);
		notifications.notifyTrade(trade.getRecipient(), NotificationType.TRADE_CANCELLED, trade);
		return Outcome.ok(view(trade));
	}

	/** Expires one trade if it is still pending and past its deadline. Used by {@link TradeExpiryJob}. */
	@Transactional
	public void expireIfDue(long tradeId) {
		trades.lockById(tradeId).ifPresent(trade -> {
			Instant now = clock.instant();
			if (trade.getStatus() == TradeStatus.PENDING && !trade.getExpiresAt().isAfter(now)) {
				expire(trade, now);
			}
		});
	}

	/** Cancels the pending trades of a player whose account is being deleted. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void cancelAllPending(Player player) {
		for (Trade trade : trades.findPendingOf(player.getId())) {
			close(trade, TradeStatus.CANCELLED);
			Player other = trade.getProposer().getId().equals(player.getId()) ? trade.getRecipient() : trade.getProposer();
			notifications.notifyTrade(other, NotificationType.TRADE_CANCELLED, trade);
		}
	}

	// ------------------------------------------------------------------ internals

	/**
	 * Null when the trade can still be acted on. A trade past its deadline is expired here, even before the job
	 * runs, and the action is refused with its current status.
	 */
	private Outcome checkPending(Trade trade) {
		Instant now = clock.instant();
		if (trade.getStatus() == TradeStatus.PENDING && !trade.getExpiresAt().isAfter(now)) {
			expire(trade, now);
		}
		if (trade.getStatus() == TradeStatus.PENDING) {
			return null;
		}
		return new Outcome(view(trade), new ApiException(HttpStatus.CONFLICT, "TRADE_NOT_PENDING",
				"The trade is no longer pending.", Map.of("status", trade.getStatus().name())));
	}

	private void expire(Trade trade, Instant now) {
		trade.setStatus(TradeStatus.EXPIRED);
		trade.setClosedAt(now);
		notifications.notifyTrade(trade.getProposer(), NotificationType.TRADE_EXPIRED, trade);
		notifications.notifyTrade(trade.getRecipient(), NotificationType.TRADE_EXPIRED, trade);
	}

	private void close(Trade trade, TradeStatus status) {
		trade.setStatus(status);
		trade.setClosedAt(clock.instant());
	}

	/**
	 * Cards exist, are tradeable, appear once per side and not on both sides, quantities are positive, the trade
	 * moves at least one card and the two players are different. Every problem is TRADE_INVALID_CARDS.
	 */
	private Map<Long, Card> checkCards(Player proposer, Player recipient, TradeRequests.Propose request) {
		List<ApiError.FieldErrorItem> errors = new ArrayList<>();
		if (proposer.getId().equals(recipient.getId())) {
			errors.add(new ApiError.FieldErrorItem("recipientUsername", "TRADE_WITH_SELF"));
		}
		if (request.offered().isEmpty() && request.requested().isEmpty()) {
			errors.add(new ApiError.FieldErrorItem("offeredCards", "TRADE_EMPTY"));
		}
		Set<Long> all = new HashSet<>();
		Set<Long> offered = new HashSet<>();
		for (int i = 0; i < request.offered().size(); i++) {
			TradeRequests.CardQuantity c = request.offered().get(i);
			if (!offered.add(c.cardId())) {
				errors.add(new ApiError.FieldErrorItem("offeredCards[" + i + "].cardId", "CARD_DUPLICATED"));
			}
			if (c.quantity() < 1) {
				errors.add(new ApiError.FieldErrorItem("offeredCards[" + i + "].quantity", "QUANTITY_INVALID"));
			}
			all.add(c.cardId());
		}
		Set<Long> requested = new HashSet<>();
		for (int i = 0; i < request.requested().size(); i++) {
			TradeRequests.CardQuantity c = request.requested().get(i);
			if (!requested.add(c.cardId())) {
				errors.add(new ApiError.FieldErrorItem("requestedCards[" + i + "].cardId", "CARD_DUPLICATED"));
			} else if (offered.contains(c.cardId())) {
				errors.add(new ApiError.FieldErrorItem("requestedCards[" + i + "].cardId", "CARD_ON_BOTH_SIDES"));
			}
			if (c.quantity() < 1) {
				errors.add(new ApiError.FieldErrorItem("requestedCards[" + i + "].quantity", "QUANTITY_INVALID"));
			}
			all.add(c.cardId());
		}
		Map<Long, Card> cards = catalog.findAll(all);
		for (Long id : all) {
			Card card = cards.get(id);
			if (card == null) {
				errors.add(new ApiError.FieldErrorItem("cardId", "CARD_NOT_FOUND"));
			} else if (!card.getRarity().isTradeable()) {
				errors.add(new ApiError.FieldErrorItem("cardId", "CARD_NOT_TRADEABLE"));
			}
		}
		if (!errors.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "TRADE_INVALID_CARDS", "The cards of the trade are invalid.",
					errors);
		}
		return cards;
	}

	private static void requireVerifiedEmail(Player player) {
		if (player.getEmailVerifiedAt() == null) {
			throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
					"Verify your email before trading.");
		}
	}

	private static int sum(Trade trade, TradeSide side) {
		return trade.getCards().stream().filter(c -> c.getId().getSide() == side).mapToInt(TradeCard::getQuantity).sum();
	}

	private static boolean isParty(Trade trade, long playerId) {
		return trade.getProposer().getId().equals(playerId) || trade.getRecipient().getId().equals(playerId);
	}

	private static TradeCard tradeCard(Trade trade, Card card, TradeSide side, int quantity) {
		TradeCard tradeCard = new TradeCard();
		tradeCard.setId(new TradeCardId(null, null, side));
		tradeCard.setTrade(trade);
		tradeCard.setCard(card);
		tradeCard.setQuantity(quantity);
		return tradeCard;
	}

	private TradeResponses.TradeView view(Trade trade) {
		return new TradeResponses.TradeView(trade.getId(), trade.getStatus(),
				new TradeResponses.PlayerRef(trade.getProposer().getId(), trade.getProposer().getUsername()),
				new TradeResponses.PlayerRef(trade.getRecipient().getId(), trade.getRecipient().getUsername()),
				cardsOf(trade, TradeSide.OFFER), cardsOf(trade, TradeSide.REQUEST), trade.getCreatedAt(),
				trade.getExpiresAt(), trade.getClosedAt());
	}

	private static List<TradeResponses.TradeCardView> cardsOf(Trade trade, TradeSide side) {
		return trade.getCards().stream().filter(c -> c.getId().getSide() == side)
				.sorted(Comparator.comparing(c -> c.getCard().getId()))
				.map(c -> new TradeResponses.TradeCardView(CatalogResponses.CardView.of(c.getCard()), c.getQuantity()))
				.toList();
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "TRADE_NOT_FOUND", "The trade does not exist.");
	}

	private static ApiException forbidden() {
		return forbidden("Only the two players of the trade can see it.");
	}

	private static ApiException forbidden(String message) {
		return new ApiException(HttpStatus.FORBIDDEN, "TRADE_FORBIDDEN", message);
	}
}
