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

	public FriendshipController(FriendshipService friendships) {
		this.friendships = friendships;
	}

	@GetMapping("/friends")
	public PageResponse<FriendshipResponses.FriendshipView> friends(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return friendships.listFriends(CurrentPlayer.id(jwt), page, size);
	}

	@GetMapping("/friend-requests")
	public PageResponse<FriendshipResponses.FriendshipView> requests(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String direction,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return friendships.listRequests(CurrentPlayer.id(jwt), direction, page, size);
	}

	@PostMapping("/friend-requests")
	@ResponseStatus(HttpStatus.CREATED)
	public FriendshipResponses.FriendshipView send(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody FriendshipRequests.Send request) {
		return friendships.send(CurrentPlayer.id(jwt), request.username());
	}

	@PostMapping("/friend-requests/{friendshipId}/accept")
	public FriendshipResponses.FriendshipView accept(@AuthenticationPrincipal Jwt jwt,
			@PathVariable long friendshipId) {
		return friendships.accept(CurrentPlayer.id(jwt), friendshipId);
	}

	@PostMapping("/friend-requests/{friendshipId}/reject")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reject(@AuthenticationPrincipal Jwt jwt, @PathVariable long friendshipId) {
		friendships.reject(CurrentPlayer.id(jwt), friendshipId);
	}

	@DeleteMapping("/friends/{friendshipId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long friendshipId) {
		friendships.remove(CurrentPlayer.id(jwt), friendshipId);
	}
}
