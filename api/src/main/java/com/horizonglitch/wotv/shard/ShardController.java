package com.horizonglitch.wotv.shard;

import com.horizonglitch.wotv.common.PageResponse;
import com.horizonglitch.wotv.security.CurrentPlayer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Shard history of api-contract-draft/03-packs-shards.md. */
@RestController
@Tag(name = "Shards")
public class ShardController {

	private final ShardService shards;

	public ShardController(ShardService shards) {
		this.shards = shards;
	}

	@Operation(summary = "Shard incomes and expenses, most recent first", security = @SecurityRequirement(name = "bearer"))
	@GetMapping("/api/v1/users/me/shard-transactions")
	public PageResponse<ShardResponses.Transaction> history(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return shards.history(CurrentPlayer.id(jwt), page, size);
	}
}
