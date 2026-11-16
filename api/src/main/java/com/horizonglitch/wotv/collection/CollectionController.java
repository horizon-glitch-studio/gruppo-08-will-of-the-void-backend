package com.horizonglitch.wotv.collection;

import com.horizonglitch.wotv.common.PageResponse;
import com.horizonglitch.wotv.security.CurrentPlayer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Collection routes of api-contract-draft/02-cards-collection-decks.md. */
@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Collection")
public class CollectionController {

	private final CollectionService collection;

	public CollectionController(CollectionService collection) {
		this.collection = collection;
	}

	@Operation(summary = "Owned cards; availableInDeck=true leaves out Wanderers whose time has ended",
			security = @SecurityRequirement(name = "bearer"))
	@GetMapping("/collection")
	public PageResponse<CollectionResponses.Item> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) Integer factionId,
			@RequestParam(required = false) Integer rarityId,
			@RequestParam(required = false) Boolean availableInDeck,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return collection.list(CurrentPlayer.id(jwt), factionId, rarityId, availableInDeck, page, size);
	}

	@Operation(summary = "An owned card", security = @SecurityRequirement(name = "bearer"))
	@GetMapping("/collection/{cardId}")
	public CollectionResponses.Item get(@AuthenticationPrincipal Jwt jwt, @PathVariable long cardId) {
		return collection.get(CurrentPlayer.id(jwt), cardId);
	}

	@Operation(summary = "Departed Wanderers, most recent first", security = @SecurityRequirement(name = "bearer"))
	@GetMapping("/archive")
	public PageResponse<CollectionResponses.ArchiveItem> archive(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return collection.archive(CurrentPlayer.id(jwt), page, size);
	}
}
