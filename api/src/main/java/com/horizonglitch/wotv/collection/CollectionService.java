package com.horizonglitch.wotv.collection;

import com.horizonglitch.wotv.card.StarterDeckCard;
import com.horizonglitch.wotv.common.ApiException;
import com.horizonglitch.wotv.common.PageResponse;
import com.horizonglitch.wotv.common.Paging;
import com.horizonglitch.wotv.player.Player;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The cards on board each Summoner's ship, and the departed Wanderers. */
@Service
public class CollectionService {

	private final CollectionCardRepository collection;
	private final MemoryArchiveRepository archive;
	private final Clock clock;

	public CollectionService(CollectionCardRepository collection, MemoryArchiveRepository archive, Clock clock) {
		this.collection = collection;
		this.archive = archive;
		this.clock = clock;
	}

	/** Gives a new Summoner the cards of the starter deck, which are permanent. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void addStarterCards(Player player, List<StarterDeckCard> starterDeck) {
		for (StarterDeckCard starter : starterDeck) {
			CollectionCard card = new CollectionCard();
			card.setPlayer(player);
			card.setCard(starter.getCard());
			card.setQuantity(starter.getQuantity());
			collection.save(card);
		}
	}

	/**
	 * The player's cards, by card id. With {@code availableInDeck} = true only the cards that can be played:
	 * a Wanderer whose time has ended is left out even before it is moved to the archive.
	 */
	@Transactional(readOnly = true)
	public PageResponse<CollectionResponses.Item> list(long playerId, Integer factionId, Integer rarityId,
			Boolean availableInDeck, Integer page, Integer size) {
		Specification<CollectionCard> spec = (root, query, cb) -> cb.equal(root.get("player").get("id"), playerId);
		if (factionId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("card").get("faction").get("id"), factionId));
		}
		if (rarityId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("card").get("rarity").get("id"), rarityId));
		}
		if (Boolean.TRUE.equals(availableInDeck)) {
			Instant now = clock.instant();
			spec = spec.and((root, query, cb) -> cb.or(cb.isNull(root.get("expiresAt")),
					cb.greaterThan(root.get("expiresAt"), now)));
		}
		return PageResponse.of(collection.findAll(spec, Paging.of(page, size, Sort.by("card.id"))),
				CollectionResponses.Item::of);
	}

	@Transactional(readOnly = true)
	public CollectionResponses.Item get(long playerId, long cardId) {
		return collection.findById_PlayerIdAndId_CardId(playerId, cardId).map(CollectionResponses.Item::of)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND",
						"The card is not in the collection."));
	}

	/** Departed Wanderers, the most recent first. */
	@Transactional(readOnly = true)
	public PageResponse<CollectionResponses.ArchiveItem> archive(long playerId, Integer page, Integer size) {
		return PageResponse.of(archive.findByPlayer_Id(playerId,
				Paging.of(page, size, Sort.by(Sort.Direction.DESC, "departedAt").and(Sort.by("id")))),
				CollectionResponses.ArchiveItem::of);
	}

	/** The copies the player owns of the given cards, by card id; cards not owned are missing from the map. */
	@Transactional(readOnly = true)
	public Map<Long, CollectionCard> owned(long playerId, Collection<Long> cardIds) {
		if (cardIds.isEmpty()) {
			return Map.of();
		}
		return collection.findOwned(playerId, cardIds).stream()
				.collect(Collectors.toMap(c -> c.getId().getCardId(), Function.identity()));
	}
}
