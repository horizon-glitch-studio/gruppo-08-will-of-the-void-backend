package com.horizonglitch.wotv.card;

import com.horizonglitch.wotv.common.ApiException;
import com.horizonglitch.wotv.common.PageResponse;
import com.horizonglitch.wotv.common.Paging;
import com.horizonglitch.wotv.config.AppProperties;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The card catalog and the reference data of the game. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

	/** Allowed values of the {@code sort} parameter; the default is the card id. */
	private static final Map<String, Sort> SORTS = Map.of(
			"id", Sort.by("id"),
			"name", Sort.by("name"),
			"attack", Sort.by(Sort.Direction.DESC, "attack").and(Sort.by("id")),
			"defense", Sort.by(Sort.Direction.DESC, "defense").and(Sort.by("id")),
			"rarity", Sort.by("rarity.sortOrder").and(Sort.by("id")));

	private final CardRepository cards;
	private final FactionRepository factions;
	private final RarityRepository rarities;
	private final FactionAdvantageRepository advantages;
	private final AppProperties.Decks deckRules;

	public CatalogService(CardRepository cards, FactionRepository factions, RarityRepository rarities,
			FactionAdvantageRepository advantages, AppProperties properties) {
		this.cards = cards;
		this.factions = factions;
		this.rarities = rarities;
		this.advantages = advantages;
		this.deckRules = properties.decks();
	}

	/** Active cards only, filtered by faction, rarity and part of the name. */
	public PageResponse<CatalogResponses.CardView> search(Integer factionId, Integer rarityId, String search,
			String sort, Integer page, Integer size) {
		Sort order = sort == null ? SORTS.get("id") : SORTS.get(sort);
		if (order == null) {
			throw ApiException.validation(List.of(new com.horizonglitch.wotv.common.ApiError.FieldErrorItem("sort",
					"SORT_INVALID")));
		}
		Specification<Card> spec = (root, query, cb) -> cb.isTrue(root.get("active"));
		if (factionId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("faction").get("id"), factionId));
		}
		if (rarityId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("rarity").get("id"), rarityId));
		}
		if (search != null && !search.isBlank()) {
			String pattern = "%" + search.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
			spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\'));
		}
		return PageResponse.of(cards.findAll(spec, Paging.of(page, size, order)), CatalogResponses.CardView::of);
	}

	public CatalogResponses.CardView card(long cardId) {
		return cards.findByIdAndActiveTrue(cardId).map(CatalogResponses.CardView::of)
				.orElseThrow(CatalogService::cardNotFound);
	}

	public CatalogResponses.GameData gameData() {
		return new CatalogResponses.GameData(
				factions.findAll(Sort.by("id")).stream().map(CatalogResponses.FactionView::of).toList(),
				rarities.findAll(Sort.by("sortOrder")).stream().map(CatalogResponses.RarityView::of).toList(),
				advantages.findAll().stream().map(CatalogResponses.AdvantageView::of).toList(),
				new CatalogResponses.DeckRulesView(deckRules.size(), deckRules.maxPerElementalFaction(),
						deckRules.maxSaved()));
	}

	/** Cards by id, active or not: copies already owned of an inactive card can still be played (CARD-10). */
	public Map<Long, Card> findAll(Collection<Long> ids) {
		return cards.findAllById(ids).stream().collect(Collectors.toMap(Card::getId, Function.identity()));
	}

	public static ApiException cardNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "The card does not exist.");
	}
}
