package com.horizonglitch.wotv.deck;

import com.horizonglitch.wotv.card.Card;
import com.horizonglitch.wotv.card.CatalogResponses;
import com.horizonglitch.wotv.card.CatalogService;
import com.horizonglitch.wotv.card.StarterDeckCard;
import com.horizonglitch.wotv.collection.CollectionCard;
import com.horizonglitch.wotv.collection.CollectionService;
import com.horizonglitch.wotv.common.ApiError;
import com.horizonglitch.wotv.common.ApiException;
import com.horizonglitch.wotv.config.AppProperties;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decks saved by the Summoners. The status is recomputed every time a deck is saved or read (DECK-07), so it
 * stays correct after trades and departed Wanderers.
 */
@Service
public class DeckService {

	static final String STARTER_DECK_NAME = "Starter deck";

	private final DeckRepository decks;
	private final CatalogService catalog;
	private final CollectionService collection;
	private final PlayerService players;
	private final AppProperties.Decks rules;
	private final Clock clock;

	public DeckService(DeckRepository decks, CatalogService catalog, CollectionService collection,
			PlayerService players, AppProperties properties, Clock clock) {
		this.decks = decks;
		this.catalog = catalog;
		this.collection = collection;
		this.players = players;
		this.rules = properties.decks();
		this.clock = clock;
	}

	/** Saves the starter deck for a new Summoner. It is valid by rule (DECK-12), checked by the backoffice. */
	@Transactional(propagation = Propagation.MANDATORY)
	public Deck createStarterDeck(Player player, List<StarterDeckCard> starterDeck) {
		Deck deck = new Deck();
		deck.setPlayer(player);
		deck.setName(STARTER_DECK_NAME);
		deck.setStatus(DeckStatus.VALID);
		for (StarterDeckCard starter : starterDeck) {
			deck.getCards().add(deckCard(deck, starter.getCard(), starter.getQuantity()));
		}
		return decks.save(deck);
	}

	@Transactional
	public List<DeckResponses.DeckView> list(long playerId) {
		Player player = players.getActive(playerId);
		return decks.findByPlayer_IdOrderById(playerId).stream().map(d -> view(d, player)).toList();
	}

	@Transactional
	public DeckResponses.DeckView get(long playerId, long deckId) {
		Player player = players.getActive(playerId);
		return view(owned(playerId, deckId), player);
	}

	/** At most {@code maxSaved} decks per player: the player's row is locked so two creations cannot pass together. */
	@Transactional
	public DeckResponses.DeckView create(long playerId, DeckRequests.Save request) {
		Player player = players.lockById(playerId);
		if (decks.countByPlayer_Id(playerId) >= rules.maxSaved()) {
			throw new ApiException(HttpStatus.CONFLICT, "DECK_LIMIT_REACHED",
					"A player can save at most " + rules.maxSaved() + " decks.");
		}
		String name = request.name().trim();
		if (decks.existsByPlayer_IdAndName(playerId, name)) {
			throw nameUsed();
		}
		Deck deck = new Deck();
		deck.setPlayer(player);
		deck.setName(name);
		fill(deck, playerId, request.cards());
		return view(saveChecked(deck), player);
	}

	/** Replaces name and cards. A defense deck that is no longer VALID stays selected but cannot defend (DECK-15). */
	@Transactional
	public DeckResponses.DeckView replace(long playerId, long deckId, DeckRequests.Save request) {
		Player player = players.getActive(playerId);
		Deck deck = owned(playerId, deckId);
		String name = request.name().trim();
		if (decks.existsByPlayer_IdAndNameAndIdNot(playerId, name, deckId)) {
			throw nameUsed();
		}
		deck.setName(name);
		deck.getCards().clear();
		decks.flush();
		fill(deck, playerId, request.cards());
		return view(saveChecked(deck), player);
	}

	/** Deleting the defense deck leaves the player without one (the foreign key sets it to null). */
	@Transactional
	public void delete(long playerId, long deckId) {
		Player player = players.getActive(playerId);
		Deck deck = owned(playerId, deckId);
		if (isDefense(player, deck)) {
			player.setDefenseDeck(null);
		}
		decks.delete(deck);
	}

	/** Only a VALID deck can become the defense deck. */
	@Transactional
	public DeckResponses.DeckView setDefense(long playerId, long deckId) {
		Player player = players.lockById(playerId);
		Deck deck = owned(playerId, deckId);
		DeckRules.Result result = check(deck, playerId);
		deck.setStatus(result.status());
		if (result.status() != DeckStatus.VALID) {
			throw new ApiException(HttpStatus.CONFLICT, "DEFENSE_DECK_INVALID",
					"Only a valid deck can be the defense deck.");
		}
		player.setDefenseDeck(deck);
		return view(deck, player);
	}

	@Transactional
	public void removeDefense(long playerId, long deckId) {
		Player player = players.lockById(playerId);
		Deck deck = owned(playerId, deckId);
		if (isDefense(player, deck)) {
			player.setDefenseDeck(null);
		}
	}

	/**
	 * Recomputes the status of every deck of the player after their collection changed (TRADE-13, DECK-08).
	 * Returns true when a deck has just become INVALID, so the caller can notify the player.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean recheckAll(Player player) {
		boolean newlyInvalid = false;
		for (Deck deck : decks.findByPlayer_IdOrderById(player.getId())) {
			DeckStatus status = check(deck, player.getId()).status();
			if (status != deck.getStatus()) {
				newlyInvalid |= status == DeckStatus.INVALID;
				deck.setStatus(status);
			}
		}
		return newlyInvalid;
	}

	/** Checks a composition without saving it; cards not owned are reported as rules broken. */
	@Transactional(readOnly = true)
	public DeckResponses.ValidationView validate(long playerId, DeckRequests.Validate request) {
		players.getActive(playerId);
		checkNoDuplicates(request.cards());
		Map<Long, Card> cards = existingCards(request.cards());
		Deck draft = new Deck();
		for (DeckRequests.CardQuantity c : request.cards()) {
			draft.getCards().add(deckCard(draft, cards.get(c.cardId()), c.quantity()));
		}
		DeckRules.Result result = check(draft, playerId);
		return new DeckResponses.ValidationView(result.status(), result.totalCards(), result.violations());
	}

	// ------------------------------------------------------------------ internals

	/**
	 * Sets the cards of the deck. A card that does not exist is 404; a card not owned, or in more copies than
	 * owned, is refused with 409 CARD_NOT_OWNED: a saved deck becomes invalid only through later events.
	 */
	private void fill(Deck deck, long playerId, List<DeckRequests.CardQuantity> requested) {
		checkNoDuplicates(requested);
		Map<Long, Card> cards = existingCards(requested);
		Map<Long, CollectionCard> owned = collection.owned(playerId, cards.keySet());
		List<ApiError.FieldErrorItem> notOwned = new ArrayList<>();
		for (int i = 0; i < requested.size(); i++) {
			DeckRequests.CardQuantity c = requested.get(i);
			CollectionCard copies = owned.get(c.cardId());
			if (copies == null || copies.getQuantity() < c.quantity()) {
				notOwned.add(new ApiError.FieldErrorItem("cards[" + i + "]", "CARD_NOT_OWNED"));
			}
		}
		if (!notOwned.isEmpty()) {
			throw new ApiException(HttpStatus.CONFLICT, "CARD_NOT_OWNED",
					"The deck uses cards, or copies, the player does not own.", notOwned);
		}
		for (DeckRequests.CardQuantity c : requested) {
			deck.getCards().add(deckCard(deck, cards.get(c.cardId()), c.quantity()));
		}
		deck.setStatus(check(deck, playerId).status());
	}

	private Deck saveChecked(Deck deck) {
		try {
			return decks.saveAndFlush(deck);
		} catch (DataIntegrityViolationException e) {
			if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("uq_deck_player_name")) {
				throw nameUsed();
			}
			throw e;
		}
	}

	private static void checkNoDuplicates(List<DeckRequests.CardQuantity> requested) {
		Set<Long> seen = new HashSet<>();
		List<ApiError.FieldErrorItem> errors = new ArrayList<>();
		for (int i = 0; i < requested.size(); i++) {
			if (!seen.add(requested.get(i).cardId())) {
				errors.add(new ApiError.FieldErrorItem("cards[" + i + "].cardId", "CARD_DUPLICATED"));
			}
		}
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}
	}

	private Map<Long, Card> existingCards(List<DeckRequests.CardQuantity> requested) {
		Map<Long, Card> cards = catalog.findAll(requested.stream().map(DeckRequests.CardQuantity::cardId).toList());
		if (cards.size() < requested.size()) {
			throw CatalogService.cardNotFound();
		}
		return cards;
	}

	private DeckRules.Result check(Deck deck, long playerId) {
		List<Long> cardIds = deck.getCards().stream().map(dc -> dc.getCard().getId()).toList();
		Map<Long, CollectionCard> owned = collection.owned(playerId, cardIds);
		Instant now = clock.instant();
		List<DeckRules.Entry> entries = deck.getCards().stream().map(dc -> {
			Card card = dc.getCard();
			CollectionCard copies = owned.get(card.getId());
			boolean playable = copies == null || copies.getExpiresAt() == null || copies.getExpiresAt().isAfter(now);
			return new DeckRules.Entry(card.getId(), dc.getQuantity(), card.getFaction().getId(),
					card.getFaction().isInert(), card.getRarity().getId(), card.getRarity().getMaxCopiesPerDeck(),
					card.getRarity().getMaxPerDeck(), copies == null ? 0 : copies.getQuantity(), playable);
		}).toList();
		return DeckRules.check(entries, rules.size(), rules.maxPerElementalFaction());
	}

	/** The current status, saved when it has changed (DECK-07, DECK-08). */
	private DeckResponses.DeckView view(Deck deck, Player player) {
		DeckRules.Result result = check(deck, player.getId());
		if (deck.getStatus() != result.status()) {
			deck.setStatus(result.status());
		}
		List<DeckResponses.DeckCardView> cards = deck.getCards().stream()
				.sorted(Comparator.comparing(dc -> dc.getCard().getId()))
				.map(dc -> new DeckResponses.DeckCardView(CatalogResponses.CardView.of(dc.getCard()), dc.getQuantity()))
				.toList();
		return new DeckResponses.DeckView(deck.getId(), deck.getName(), result.status(), cards, result.totalCards(),
				isDefense(player, deck), deck.getUpdatedAt(), result.violations());
	}

	private Deck owned(long playerId, long deckId) {
		return decks.findByIdAndPlayer_Id(deckId, playerId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DECK_NOT_FOUND", "The deck does not exist."));
	}

	private static boolean isDefense(Player player, Deck deck) {
		return player.getDefenseDeck() != null && deck.getId() != null
				&& deck.getId().equals(player.getDefenseDeck().getId());
	}

	private static DeckCard deckCard(Deck deck, Card card, int quantity) {
		DeckCard deckCard = new DeckCard();
		deckCard.setDeck(deck);
		deckCard.setCard(card);
		deckCard.setQuantity(quantity);
		return deckCard;
	}

	private static ApiException nameUsed() {
		return new ApiException(HttpStatus.CONFLICT, "DECK_NAME_ALREADY_USED", "Another deck has this name.");
	}
}
