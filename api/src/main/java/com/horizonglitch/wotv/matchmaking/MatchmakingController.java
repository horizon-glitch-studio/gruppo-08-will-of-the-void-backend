package com.horizonglitch.wotv.matchmaking;

import com.horizonglitch.wotv.battle.BattleMode;
import com.horizonglitch.wotv.battle.BattleResponses;
import com.horizonglitch.wotv.security.CurrentPlayer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Matchmaking routes of api-contract-draft/05-battles-matchmaking.md. */
@RestController
@RequestMapping("/api/v1/matchmaking/opponents")
@Tag(name = "Matchmaking")
@SecurityRequirements(@SecurityRequirement(name = "bearer"))
public class MatchmakingController {

	private final MatchmakingService matchmaking;

	public MatchmakingController(MatchmakingService matchmaking) {
		this.matchmaking = matchmaking;
	}

	@Operation(summary = "The saved list of 5 opponents for QUICK or RANKED")
	@GetMapping
	public MatchmakingResponses.OpponentListView current(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) BattleMode mode) {
		return matchmaking.current(CurrentPlayer.id(jwt), mode);
	}

	@Operation(summary = "A new list, at most once every 5 seconds")
	@PostMapping("/refresh")
	public MatchmakingResponses.OpponentListView refresh(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody MatchmakingRequests.Refresh request) {
		return matchmaking.refresh(CurrentPlayer.id(jwt), request.mode());
	}

	@Operation(summary = "Attack an opponent of the list: played at once, or a challenge in QUICK without defense deck")
	@PostMapping("/{opponentId}/battle")
	@ResponseStatus(HttpStatus.CREATED)
	public BattleResponses.BattleView attack(@AuthenticationPrincipal Jwt jwt, @PathVariable long opponentId,
			@Valid @RequestBody MatchmakingRequests.Start request) {
		return matchmaking.attack(CurrentPlayer.id(jwt), opponentId, request);
	}
}
