package com.horizonglitch.wotv.pack;

import com.horizonglitch.wotv.card.Card;
import com.horizonglitch.wotv.card.CatalogResponses;
import com.horizonglitch.wotv.card.CatalogService;
import com.horizonglitch.wotv.card.Rarity;
import com.horizonglitch.wotv.collection.CollectionService;
import com.horizonglitch.wotv.common.ApiException;
import com.horizonglitch.wotv.common.PageResponse;
import com.horizonglitch.wotv.common.Paging;
import com.horizonglitch.wotv.config.AppProperties;
import com.horizonglitch.wotv.player.Player;
import com.horizonglitch.wotv.player.PlayerService;
import com.horizonglitch.wotv.shard.ShardService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Shop: pack types on sale and pack openings. Buying and opening are one transaction: Shards, opening,
 * cards, collection and guarantee counters are all saved, or none of them.
 */
@Service
public class PackService {

	private static final Logger log = LoggerFactory.getLogger(PackService.class);

	private final PackTypeRepository packTypes;
	private final EventPackCardRepository eventCards;
	private final PackOpeningRepository openings;
	private final PackGuaranteeRepository guarantees;
	private final CatalogService catalog;
	private final CollectionService collection;
	private final ShardService shards;
	private final PlayerService players;
	private final AppProperties.Packs rules;
	private final RandomGenerator random;
	private final Clock clock;

	public PackService(PackTypeRepository packTypes, EventPackCardRepository eventCards,
			PackOpeningRepository openings, PackGuaranteeRepository guarantees, CatalogService catalog,
			CollectionService collection, ShardService shards, PlayerService players, AppProperties properties,
			RandomGenerator random, Clock clock) {
		this.packTypes = packTypes;
		this.eventCards = eventCards;
		this.openings = openings;
		this.guarantees = guarantees;
		this.catalog = catalog;
		this.collection = collection;
		this.shards = shards;
		this.players = players;
		this.rules = properties.packs();
		this.random = random;
		this.clock = clock;
	}

	/** Pack types on sale now: the standard packs and the event pack inside its window (PACK-02, PACK-03). */
	@Transactional(readOnly = true)
	public List<PackResponses.PackView> available() {
		Instant now = clock.instant();
		return packTypes.findByActiveTrueOrderById().stream().filter(p -> onSale(p, now)).map(p -> view(p, now))
				.toList();
	}

	@Transactional(readOnly = true)
	public PackResponses.PackView get(int packTypeId) {
		return view(packTypes.findByIdAndActiveTrue(packTypeId).orElseThrow(PackService::packNotFound), clock.instant());
	}

	/**
	 * Buys and opens a pack. The guarantee counters are locked first, so two packs of the same player are drawn
	 * one after the other; the client sends no card or rarity.
	 */
	@Transactional
	public PackResponses.Opening open(long playerId, int packTypeId) {
		Player player = players.getActive(playerId);
		PackGuarantee counters = guarantees.lockByPlayerId(playerId)
				.orElseThrow(() -> new IllegalStateException("Player " + playerId + " has no pack_guarantee row"));
		PackType pack = packTypes.findByIdAndActiveTrue(packTypeId).orElseThrow(PackService::packNotFound);
		Instant now = clock.instant();
		if (!onSale(pack, now)) {
			throw new ApiException(HttpStatus.CONFLICT, "PACK_NOT_AVAILABLE", "This pack is not on sale now.");
		}

		PackOpening opening = new PackOpening();
		opening.setPlayer(player);
		opening.setPackType(pack);
		opening.setShardsSpent(pack.getShardCost());
		openings.save(opening);
		shards.chargePack(player, opening, pack.getShardCost());

		boolean event = pack.getKind() == PackKind.EVENT;
		PackDraw.Result result = draw(pack, event, counters);
		opening.setGuarantee(result.guarantee());
		counters.setPacksWithoutChampion((short) result.counters().withoutChampion());
		counters.setStandardPacksWithoutElect((short) result.counters().standardWithoutElect());
		counters.setEventPacksWithoutWanderer((short) result.counters().eventWithoutWanderer());

		Map<Long, Integer> quantities = new LinkedHashMap<>();
		result.cards().forEach(d -> quantities.merge(d.cardId(), 1, Integer::sum));
		Map<Long, Card> cards = catalog.findAll(quantities.keySet());
		List<PackResponses.OpenedCard> opened = new ArrayList<>();
		for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
			Card card = cards.get(entry.getKey());
			PackOpeningCard drawn = new PackOpeningCard();
			drawn.setPackOpening(opening);
			drawn.setCard(card);
			drawn.setQuantity(entry.getValue());
			opening.getCards().add(drawn);
			CollectionService.Added added = collection.addFromPack(player, card, entry.getValue());
			opened.add(new PackResponses.OpenedCard(CatalogResponses.CardView.of(card), entry.getValue(),
					added.expiresAt(), added.isNew()));
		}
		openings.flush();

		return new PackResponses.Opening(opening.getId(), ref(pack), opening.getShardsSpent(),
				shards.balanceOf(playerId), opening.getGuarantee(), opened, opening.getCreatedAt());
	}

	@Transactional(readOnly = true)
	public PageResponse<PackResponses.OpeningSummary> history(long playerId, Integer page, Integer size) {
		return PageResponse.of(openings.findByPlayer_Id(playerId,
				Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")))),
				o -> new PackResponses.OpeningSummary(o.getId(), ref(o.getPackType()), o.getShardsSpent(),
						o.getGuarantee(), o.getCards().stream().mapToInt(PackOpeningCard::getQuantity).sum(),
						o.getCreatedAt()));
	}

	@Transactional(readOnly = true)
	public PackResponses.OpeningDetail opening(long playerId, long openingId) {
		PackOpening o = openings.findByIdAndPlayer_Id(openingId, playerId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PACK_OPENING_NOT_FOUND",
						"The pack opening does not exist."));
		List<PackResponses.DrawnCard> cards = o.getCards().stream()
				.map(c -> new PackResponses.DrawnCard(CatalogResponses.CardView.of(c.getCard()), c.getQuantity()))
				.toList();
		return new PackResponses.OpeningDetail(o.getId(), ref(o.getPackType()), o.getShardsSpent(), o.getGuarantee(),
				cards, o.getCreatedAt());
	}

	// ------------------------------------------------------------------ internals

	private PackDraw.Result draw(PackType pack, boolean event, PackGuarantee counters) {
		Rarity champion = rarity(rules.championRarity());
		Rarity elect = rarity(rules.electRarity());
		Rarity wanderer = rarity(rules.wandererRarity());

		Map<Integer, Integer> sortOrders = new HashMap<>();
		for (Rarity r : List.of(champion, elect, wanderer)) {
			sortOrders.put(r.getId(), r.getSortOrder());
		}
		List<PackDraw.SlotOdds> slots = pack.getSlots().stream().map(slot -> new PackDraw.SlotOdds(
				slot.getId().getPosition(),
				slot.getRarities().stream().map(r -> {
					sortOrders.put(r.getRarity().getId(), r.getRarity().getSortOrder());
					return new PackDraw.RarityOdds(r.getRarity().getId(), r.getProbability());
				}).toList())).toList();

		List<Long> eventWanderers = event ? eventCards.findCardIds(pack.getId()) : List.of();
		Map<Integer, List<Long>> cache = new HashMap<>();
		PackDraw.Candidates candidates = rarityId -> rarityId == wanderer.getId() ? eventWanderers
				: cache.computeIfAbsent(rarityId, catalog::activeCardIdsOfRarity);

		try {
			return PackDraw.open(slots, event,
					new PackDraw.Counters(counters.getPacksWithoutChampion(), counters.getStandardPacksWithoutElect(),
							counters.getEventPacksWithoutWanderer()),
					new PackDraw.Thresholds(rules.championPacks(), rules.electPacks(), rules.wandererPacks()),
					new PackDraw.Rarities(champion.getId(), elect.getId(), wanderer.getId(), sortOrders),
					candidates, random);
		} catch (PackDraw.NoCardsException e) {
			log.error("Pack {} cannot be opened: {}", pack.getId(), e.getMessage());
			throw openingFailed();
		}
	}

	private Rarity rarity(String name) {
		return catalog.rarityNamed(name).orElseThrow(() -> {
			log.error("Rarity '{}' of app.packs is missing from the catalog", name);
			return openingFailed();
		});
	}

	private PackResponses.PackView view(PackType pack, Instant now) {
		List<PackResponses.SlotView> slots = pack.getSlots().stream().map(slot -> new PackResponses.SlotView(
				slot.getId().getPosition(),
				slot.getRarities().stream()
						.sorted((a, b) -> Integer.compare(a.getRarity().getSortOrder(), b.getRarity().getSortOrder()))
						.map(r -> new PackResponses.RarityOddsView(r.getRarity().getId(), r.getRarity().getName(),
								r.getProbability()))
						.toList())).toList();
		boolean event = pack.getKind() == PackKind.EVENT;
		List<PackResponses.GuaranteeView> guaranteeViews = new ArrayList<>();
		guaranteeViews.add(new PackResponses.GuaranteeView(GuaranteeType.CHAMPION, rules.championPacks()));
		guaranteeViews.add(event ? new PackResponses.GuaranteeView(GuaranteeType.WANDERER, rules.wandererPacks())
				: new PackResponses.GuaranteeView(GuaranteeType.ELECT, rules.electPacks()));
		List<CatalogResponses.CardView> wanderers = event
				? eventCards.findWithCards(pack.getId()).stream().map(e -> CatalogResponses.CardView.of(e.getCard())).toList()
				: List.of();
		return new PackResponses.PackView(pack.getId(), pack.getName(), pack.getKind(), pack.getShardCost(),
				onSale(pack, now), pack.getAvailableFrom(), pack.getAvailableUntil(), slots, guaranteeViews, wanderers);
	}

	private static boolean onSale(PackType pack, Instant now) {
		if (!pack.isActive()) {
			return false;
		}
		if (pack.getKind() == PackKind.STANDARD) {
			return true;
		}
		return !now.isBefore(pack.getAvailableFrom()) && now.isBefore(pack.getAvailableUntil());
	}

	private static PackResponses.PackRef ref(PackType pack) {
		return new PackResponses.PackRef(pack.getId(), pack.getName(), pack.getKind());
	}

	private static ApiException packNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "PACK_NOT_FOUND", "The pack does not exist.");
	}

	private static ApiException openingFailed() {
		return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PACK_OPENING_FAILED",
				"The pack could not be opened; no Shards were spent.");
	}
}
