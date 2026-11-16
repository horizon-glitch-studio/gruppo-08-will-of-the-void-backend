package com.horizonglitch.wotv.controllers;

import java.net.URI;
import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horizonglitch.wotv.payload.response.DeckApiModels.DeckView;
import com.horizonglitch.wotv.payload.response.DeckApiModels.SaveDeckRequest;
import com.horizonglitch.wotv.payload.response.DeckApiModels.ValidateDeckRequest;
import com.horizonglitch.wotv.payload.response.DeckApiModels.ValidationResult;
import com.horizonglitch.wotv.services.DeckService;

@RestController
@RequestMapping("/api/v1/users/me/decks")
public class DeckController {

	private final DeckService deckService;

	public DeckController(DeckService deckService) {
		this.deckService = deckService;
	}

	@GetMapping
	public List<DeckView> getDecks(Principal principal) {
		return deckService.getDecks(principal.getName());
	}

	@PostMapping
	public ResponseEntity<DeckView> createDeck(
			Principal principal,
			@Valid @RequestBody SaveDeckRequest request) {
		DeckView deck = deckService.createDeck(principal.getName(), request);
		return ResponseEntity.created(URI.create("/api/v1/users/me/decks/" + deck.id())).body(deck);
	}

	@GetMapping("/{deckId}")
	public DeckView getDeck(Principal principal, @PathVariable long deckId) {
		return deckService.getDeck(principal.getName(), deckId);
	}

	@PutMapping("/{deckId}")
	public DeckView updateDeck(
			Principal principal,
			@PathVariable long deckId,
			@Valid @RequestBody SaveDeckRequest request) {
		return deckService.updateDeck(principal.getName(), deckId, request);
	}

	@DeleteMapping("/{deckId}")
	public ResponseEntity<Void> deleteDeck(Principal principal, @PathVariable long deckId) {
		deckService.deleteDeck(principal.getName(), deckId);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{deckId}/defense")
	public DeckView activateDefenseDeck(Principal principal, @PathVariable long deckId) {
		return deckService.activateDefenseDeck(principal.getName(), deckId);
	}

	@DeleteMapping("/{deckId}/defense")
	public ResponseEntity<Void> deactivateDefenseDeck(Principal principal, @PathVariable long deckId) {
		deckService.deactivateDefenseDeck(principal.getName(), deckId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/validate")
	public ValidationResult validateDeck(
			Principal principal,
			@Valid @RequestBody ValidateDeckRequest request) {
		return deckService.validateDeck(principal.getName(), request.cards());
	}
}