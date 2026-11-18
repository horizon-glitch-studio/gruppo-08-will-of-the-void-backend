package com.horizonglitch.wotv.battle;

import com.horizonglitch.wotv.common.PageResponse;
import com.horizonglitch.wotv.security.CurrentPlayer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
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

/** Battle routes of api-contract-draft/05-battles-matchmaking.md. Quick and Ranked battles start from matchmaking. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Battles")
@SecurityRequirements(@SecurityRequirement(name = "bearer"))
public class BattleController {

	private final BattleService battles;

	public BattleController(BattleService battles) {
		this.battles = battles;
	}

	@Operation(summary = "Friend match: played at once against the friend's defense deck, otherwise a challenge")
	@PostMapping("/battles/friend")
	@ResponseStatus(HttpStatus.CREATED)
	public BattleResponses.BattleView friend(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody BattleRequests.Friend request) {
		return battles.challengeFriend(CurrentPlayer.id(jwt), request);
	}

	@Operation(summary = "A battle or challenge of the player")
	@GetMapping("/battles/{battleId}")
	public BattleResponses.BattleView get(@AuthenticationPrincipal Jwt jwt, @PathVariable long battleId) {
		return battles.get(CurrentPlayer.id(jwt), battleId);
	}

	@Operation(summary = "Accept a challenge with one of the player's decks: the match is played at once")
	@PostMapping("/battles/{battleId}/accept")
	public BattleResponses.BattleView accept(@AuthenticationPrincipal Jwt jwt, @PathVariable long battleId,
			@Valid @RequestBody BattleRequests.DeckChoice request) {
		return result(battles.accept(CurrentPlayer.id(jwt), battleId, request));
	}

	@Operation(summary = "Reject a challenge")
	@PostMapping("/battles/{battleId}/reject")
	public BattleResponses.BattleView reject(@AuthenticationPrincipal Jwt jwt, @PathVariable long battleId) {
		return result(battles.reject(CurrentPlayer.id(jwt), battleId));
	}

	@Operation(summary = "Cancel a challenge as challenger, before it is accepted")
	@PostMapping("/battles/{battleId}/cancel")
	public BattleResponses.BattleView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable long battleId) {
		return result(battles.cancel(CurrentPlayer.id(jwt), battleId));
	}

	@Operation(summary = "Rematch of a played Friend or Quick battle")
	@PostMapping("/battles/{battleId}/rematch")
	@ResponseStatus(HttpStatus.CREATED)
	public BattleResponses.BattleView rematch(@AuthenticationPrincipal Jwt jwt, @PathVariable long battleId,
			@Valid @RequestBody BattleRequests.DeckChoice request) {
		return battles.rematch(CurrentPlayer.id(jwt), battleId, request);
	}

	@Operation(summary = "Final result of a played battle, with Energy and trophy changes")
	@GetMapping("/battles/{battleId}/result")
	public BattleResponses.ResultView result(@AuthenticationPrincipal Jwt jwt, @PathVariable long battleId) {
		return battles.result(CurrentPlayer.id(jwt), battleId);
	}

	@Operation(summary = "Replay of a played battle, as a Battle Viewer document; kept for 90 days")
	@GetMapping("/battles/{battleId}/replay")
	public Map<String, Object> replay(@AuthenticationPrincipal Jwt jwt, @PathVariable long battleId) {
		return battles.replay(CurrentPlayer.id(jwt), battleId);
	}

	@Operation(summary = "The player's battles and challenges, newest first; result is WIN, LOSS or DRAW")
	@GetMapping("/users/me/battles")
	public PageResponse<BattleResponses.BattleView> history(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) BattleMode mode,
			@RequestParam(required = false) BattleStatus status,
			@RequestParam(required = false) String result,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return battles.history(CurrentPlayer.id(jwt), mode, status, result, from, to, page, size);
	}

	/** The error is thrown here, after the transaction has saved an expired challenge. */
	private static BattleResponses.BattleView result(BattleService.Outcome outcome) {
		if (outcome.error() != null) {
			throw outcome.error();
		}
		return outcome.battle();
	}
}
