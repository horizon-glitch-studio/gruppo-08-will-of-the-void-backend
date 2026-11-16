package com.horizonglitch.wotv.card;

import com.horizonglitch.wotv.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public catalog routes of api-contract-draft/02-cards-collection-decks.md. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catalog")
public class CatalogController {

	private final CatalogService catalog;

	public CatalogController(CatalogService catalog) {
		this.catalog = catalog;
	}

	@Operation(summary = "Active cards; sort is id, name, attack, defense or rarity")
	@GetMapping("/cards")
	public PageResponse<CatalogResponses.CardView> cards(
			@RequestParam(required = false) Integer factionId,
			@RequestParam(required = false) Integer rarityId,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String sort,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return catalog.search(factionId, rarityId, search, sort, page, size);
	}

	@Operation(summary = "An active card")
	@GetMapping("/cards/{cardId}")
	public CatalogResponses.CardView card(@PathVariable long cardId) {
		return catalog.card(cardId);
	}

	@Operation(summary = "Factions, rarities, faction advantages and deck limits")
	@GetMapping("/game-data")
	public CatalogResponses.GameData gameData() {
		return catalog.gameData();
	}
}
