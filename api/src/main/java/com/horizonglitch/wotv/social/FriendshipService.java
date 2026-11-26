package com.horizonglitch.wotv.social;

import com.horizonglitch.wotv.common.ApiException;
import com.horizonglitch.wotv.common.PageResponse;
import com.horizonglitch.wotv.common.Paging;
import com.horizonglitch.wotv.notification.NotificationService;
import com.horizonglitch.wotv.notification.NotificationType;
import com.horizonglitch.wotv.player.Player;
import com.horizonglitch.wotv.player.PlayerRepository;
import java.time.Clock;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendshipService {

	private final FriendshipRepository friendships;
	private final PlayerRepository players;
	private final NotificationService notifications;
	private final Clock clock;

	public FriendshipService(FriendshipRepository friendships, PlayerRepository players,
			NotificationService notifications, Clock clock) {
		this.friendships = friendships;
		this.players = players;
		this.notifications = notifications;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<FriendshipResponses.FriendshipView> listFriends(long playerId, Integer page, Integer size) {
		return PageResponse.of(friendships.findFriends(playerId,
				Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")))),
				FriendshipResponses.FriendshipView::of);
	}

	@Transactional(readOnly = true)
	public PageResponse<FriendshipResponses.FriendshipView> listRequests(long playerId, String direction,
			Integer page, Integer size) {
		if (direction != null && !direction.equals("received") && !direction.equals("sent")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "FIELD_INVALID",
					"The direction must be received or sent.");
		}
		return PageResponse.of(friendships.findRequests(playerId, direction,
				Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")))),
				FriendshipResponses.FriendshipView::of);
	}

	@Transactional
	public FriendshipResponses.FriendshipView send(long playerId, String username) {
		Player requester = players.findByIdAndDeletedAtIsNull(playerId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAYER_NOT_FOUND", "The player does not exist."));
		Player addressee = players.findActiveByUsername(username)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAYER_NOT_FOUND", "The player does not exist."));
		if (requester.getId().equals(addressee.getId())) {
			throw new ApiException(HttpStatus.CONFLICT, "FRIENDSHIP_ALREADY_EXISTS", "A player cannot send a request to themselves.");
		}
		if (friendships.findBetween(requester.getId(), addressee.getId()).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "FRIENDSHIP_ALREADY_EXISTS", "A friendship or friend request already exists.");
		}
		if (friendships.countPendingSent(requester.getId()) >= 10) {
			throw new ApiException(HttpStatus.CONFLICT, "FRIEND_REQUEST_LIMIT", "The player has 10 pending sent requests.");
		}
		Friendship friendship = new Friendship();
		friendship.setRequester(requester);
		friendship.setAddressee(addressee);
		friendship.setStatus(FriendshipStatus.PENDING);
		friendships.save(friendship);
		notifications.notifyFriendship(addressee, NotificationType.FRIEND_REQUEST_RECEIVED, friendship);
		return FriendshipResponses.FriendshipView.of(friendship);
	}

	@Transactional
	public FriendshipResponses.FriendshipView accept(long playerId, long friendshipId) {
		Friendship friendship = friendships.findById(friendshipId).orElseThrow(FriendshipService::notFound);
		if (!friendship.getAddressee().getId().equals(playerId)) {
			throw forbidden();
		}
		if (friendship.getStatus() != FriendshipStatus.PENDING) {
			throw new ApiException(HttpStatus.CONFLICT, "FRIEND_REQUEST_NOT_PENDING", "The friend request is no longer pending.");
		}
		if (friendships.countFriends(friendship.getRequester().getId()) >= 100
				|| friendships.countFriends(friendship.getAddressee().getId()) >= 100) {
			throw new ApiException(HttpStatus.CONFLICT, "FRIEND_LIMIT", "A player already has 100 friends.");
		}
		friendship.setStatus(FriendshipStatus.ACCEPTED);
		friendship.setAcceptedAt(clock.instant());
		friendships.save(friendship);
		notifications.notifyFriendship(friendship.getRequester(), NotificationType.FRIEND_REQUEST_ACCEPTED, friendship);
		return FriendshipResponses.FriendshipView.of(friendship);
	}

	@Transactional
	public void reject(long playerId, long friendshipId) {
		Friendship friendship = friendships.findById(friendshipId).orElseThrow(FriendshipService::notFound);
		if (!friendship.getAddressee().getId().equals(playerId)) {
			throw forbidden();
		}
		if (friendship.getStatus() != FriendshipStatus.PENDING) {
			throw new ApiException(HttpStatus.CONFLICT, "FRIEND_REQUEST_NOT_PENDING", "The friend request is no longer pending.");
		}
		friendships.delete(friendship);
	}

	@Transactional
	public void remove(long playerId, long friendshipId) {
		Friendship friendship = friendships.findById(friendshipId).orElseThrow(FriendshipService::notFound);
		boolean participant = friendship.getRequester().getId().equals(playerId)
				|| friendship.getAddressee().getId().equals(playerId);
		if (!participant) {
			throw forbidden();
		}
		friendships.delete(friendship);
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "FRIENDSHIP_NOT_FOUND", "The friendship does not exist.");
	}

	private static ApiException forbidden() {
		return new ApiException(HttpStatus.FORBIDDEN, "FRIENDSHIP_FORBIDDEN", "The friendship does not belong to the player.");
	}
}
