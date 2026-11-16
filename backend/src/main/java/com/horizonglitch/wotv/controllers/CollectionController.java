package com.horizonglitch.wotv.controllers;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.horizonglitch.wotv.payload.response.ApiPage;
import com.horizonglitch.wotv.payload.response.ArchivedCardView;
import com.horizonglitch.wotv.payload.response.CollectionCardView;
import com.horizonglitch.wotv.services.CollectionService;

@RestController
@RequestMapping("/api/v1/users/me")
public class CollectionController {

	private final CollectionService collectionService;

	public CollectionController(CollectionService collectionService) {
		this.collectionService = collectionService;
	}

	@GetMapping("/collection")
	public ApiPage<CollectionCardView> getCollection(
			Principal principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) Integer factionId,
			@RequestParam(required = false) Integer rarityId,
			@RequestParam(required = false) Boolean availableInDeck) {
		return collectionService.getCollection(
				principal.getName(),
				page,
				size,
				factionId,
				rarityId,
				availableInDeck);
	}

	@GetMapping("/collection/{cardId}")
	public CollectionCardView getCollectionCard(Principal principal, @PathVariable long cardId) {
		return collectionService.getCollectionCard(principal.getName(), cardId);
	}

	@GetMapping("/archive")
	public ApiPage<ArchivedCardView> getArchive(
			Principal principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return collectionService.getArchive(principal.getName(), page, size);
	}
}
