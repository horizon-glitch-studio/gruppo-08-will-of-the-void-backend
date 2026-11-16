package com.horizonglitch.wotv.pack;

import com.horizonglitch.wotv.common.PageResponse;
import com.horizonglitch.wotv.security.CurrentPlayer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Pack routes of api-contract-draft/03-packs-shards.md. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Packs")
@SecurityRequirements(@SecurityRequirement(name = "bearer"))
public class PackController {

	private final PackService packs;

	public PackController(PackService packs) {
		this.packs = packs;
	}

	@Operation(summary = "Pack types on sale now, with slot odds and guarantees")
	@GetMapping("/packs")
	public List<PackResponses.PackView> available() {
		return packs.available();
	}

	@Operation(summary = "A pack type, on sale or not")
	@GetMapping("/packs/{packTypeId}")
	public PackResponses.PackView get(@PathVariable int packTypeId) {
		return packs.get(packTypeId);
	}

	@Operation(summary = "Buy and open a pack in one transaction")
	@PostMapping("/packs/{packTypeId}/open")
	@ResponseStatus(HttpStatus.CREATED)
	public PackResponses.Opening open(@AuthenticationPrincipal Jwt jwt, @PathVariable int packTypeId) {
		return packs.open(CurrentPlayer.id(jwt), packTypeId);
	}

	@Operation(summary = "Packs opened, most recent first")
	@GetMapping("/users/me/pack-openings")
	public PageResponse<PackResponses.OpeningSummary> history(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return packs.history(CurrentPlayer.id(jwt), page, size);
	}

	@Operation(summary = "The cards of a pack opening")
	@GetMapping("/users/me/pack-openings/{openingId}")
	public PackResponses.OpeningDetail opening(@AuthenticationPrincipal Jwt jwt, @PathVariable long openingId) {
		return packs.opening(CurrentPlayer.id(jwt), openingId);
	}
}
