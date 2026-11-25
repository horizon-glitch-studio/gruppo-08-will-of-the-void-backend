package com.horizonglitch.wotv.social;

import com.horizonglitch.wotv.common.PageResponse;
import com.horizonglitch.wotv.security.CurrentPlayer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Friendships")
@SecurityRequirement(name = "bearer")
public class FriendshipController {
	private final FriendshipService friendships;

	public FriendshipController(FriendshipService friendships) { this.friendships = friendships; }

	@Operation(summary = "Accepted friends")
	@GetMapping("/friends")
	public PageResponse<FriendshipResponses.Friend> friends(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return friendships.friends(CurrentPlayer.id(jwt), page, size);
	}

	@Operation(summary = "Received or sent friend requests")
	@GetMapping("/friend-requests")
	public PageResponse<FriendshipResponses.Request> requests(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "incoming") String direction,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return friendships.requests(CurrentPlayer.id(jwt), direction, page, size);
	}

	@Operation(summary = "Send a friend request")
	@PostMapping("/friend-requests")
	@ResponseStatus(HttpStatus.CREATED)
	public FriendshipResponses.Request send(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody FriendshipRequests.Create request) {
		return friendships.send(CurrentPlayer.id(jwt), request);
	}

	@Operation(summary = "Accept a received friend request")
	@PostMapping("/friend-requests/{friendshipId}/accept")
	public FriendshipResponses.Friend accept(@AuthenticationPrincipal Jwt jwt, @PathVariable long friendshipId) {
		return friendships.accept(CurrentPlayer.id(jwt), friendshipId);
	}

	@Operation(summary = "Reject a received friend request")
	@PostMapping("/friend-requests/{friendshipId}/reject")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reject(@AuthenticationPrincipal Jwt jwt, @PathVariable long friendshipId) {
		friendships.reject(CurrentPlayer.id(jwt), friendshipId);
	}

	@Operation(summary = "Remove a friend or cancel a sent request")
	@DeleteMapping("/friends/{friendshipId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long friendshipId) {
		friendships.remove(CurrentPlayer.id(jwt), friendshipId);
	}
}
