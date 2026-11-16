package com.horizonglitch.wotv.trade;

import com.horizonglitch.wotv.common.PageResponse;
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

/** Trade routes of api-contract-draft/04-trades.md. */
@RestController
@RequestMapping("/api/v1/trades")
@Tag(name = "Trades")
@SecurityRequirements(@SecurityRequirement(name = "bearer"))
public class TradeController {

	private final TradeService trades;

	public TradeController(TradeService trades) {
		this.trades = trades;
	}

	@Operation(summary = "The player's trades; direction is incoming or outgoing")
	@GetMapping
	public PageResponse<TradeResponses.TradeView> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) TradeStatus status,
			@RequestParam(required = false) String direction,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return trades.list(CurrentPlayer.id(jwt), status, direction, page, size);
	}

	@Operation(summary = "Propose a trade; it expires after 48 hours")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TradeResponses.TradeView propose(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody TradeRequests.Propose request) {
		return trades.propose(CurrentPlayer.id(jwt), request);
	}

	@Operation(summary = "A trade of the player")
	@GetMapping("/{tradeId}")
	public TradeResponses.TradeView get(@AuthenticationPrincipal Jwt jwt, @PathVariable long tradeId) {
		return trades.get(CurrentPlayer.id(jwt), tradeId);
	}

	@Operation(summary = "Accept as recipient: the cards move at once")
	@PostMapping("/{tradeId}/accept")
	public TradeResponses.TradeView accept(@AuthenticationPrincipal Jwt jwt, @PathVariable long tradeId) {
		return result(trades.accept(CurrentPlayer.id(jwt), tradeId));
	}

	@Operation(summary = "Reject as recipient")
	@PostMapping("/{tradeId}/reject")
	public TradeResponses.TradeView reject(@AuthenticationPrincipal Jwt jwt, @PathVariable long tradeId) {
		return result(trades.reject(CurrentPlayer.id(jwt), tradeId));
	}

	@Operation(summary = "Cancel as proposer")
	@PostMapping("/{tradeId}/cancel")
	public TradeResponses.TradeView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable long tradeId) {
		return result(trades.cancel(CurrentPlayer.id(jwt), tradeId));
	}

	/** The error is thrown here, after the transaction has saved an expired or failed trade. */
	private static TradeResponses.TradeView result(TradeService.Outcome outcome) {
		if (outcome.error() != null) {
			throw outcome.error();
		}
		return outcome.trade();
	}
}
