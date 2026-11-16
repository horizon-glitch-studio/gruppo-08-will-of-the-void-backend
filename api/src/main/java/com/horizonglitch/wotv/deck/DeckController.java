package com.horizonglitch.wotv.deck;

import com.horizonglitch.wotv.security.CurrentPlayer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Deck routes of api-contract-draft/02-cards-collection-decks.md. */
@RestController
@RequestMapping("/api/v1/users/me/decks")
@Tag(name = "Decks")
@io.swagger.v3.oas.annotations.security.SecurityRequirements(@SecurityRequirement(name = "bearer"))
public class DeckController {

	private final DeckService decks;

	public DeckController(DeckService decks) {
		this.decks = decks;
	}

	@Operation(summary = "The player's decks")
	@GetMapping
	public List<DeckResponses.DeckView> list(@AuthenticationPrincipal Jwt jwt) {
		return decks.list(CurrentPlayer.id(jwt));
	}

	@Operation(summary = "Create a deck (at most 20)")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DeckResponses.DeckView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeckRequests.Save request) {
		return decks.create(CurrentPlayer.id(jwt), request);
	}

	@Operation(summary = "Check a composition without saving it")
	@PostMapping("/validate")
	public DeckResponses.ValidationView validate(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody DeckRequests.Validate request) {
		return decks.validate(CurrentPlayer.id(jwt), request);
	}

	@Operation(summary = "A deck with its current status")
	@GetMapping("/{deckId}")
	public DeckResponses.DeckView get(@AuthenticationPrincipal Jwt jwt, @PathVariable long deckId) {
		return decks.get(CurrentPlayer.id(jwt), deckId);
	}

	@Operation(summary = "Replace name and cards")
	@PutMapping("/{deckId}")
	public DeckResponses.DeckView replace(@AuthenticationPrincipal Jwt jwt, @PathVariable long deckId,
			@Valid @RequestBody DeckRequests.Save request) {
		return decks.replace(CurrentPlayer.id(jwt), deckId, request);
	}

	@Operation(summary = "Delete a deck")
	@DeleteMapping("/{deckId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long deckId) {
		decks.delete(CurrentPlayer.id(jwt), deckId);
	}

	@Operation(summary = "Make a VALID deck the defense deck")
	@PutMapping("/{deckId}/defense")
	public DeckResponses.DeckView setDefense(@AuthenticationPrincipal Jwt jwt, @PathVariable long deckId) {
		return decks.setDefense(CurrentPlayer.id(jwt), deckId);
	}

	@Operation(summary = "Stop using the deck as defense deck")
	@DeleteMapping("/{deckId}/defense")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeDefense(@AuthenticationPrincipal Jwt jwt, @PathVariable long deckId) {
		decks.removeDefense(CurrentPlayer.id(jwt), deckId);
	}
}
