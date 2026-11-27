package com.horizonglitch.wotv.social;

import com.horizonglitch.wotv.common.ApiError;
import com.horizonglitch.wotv.common.ApiException;
import com.horizonglitch.wotv.common.PageResponse;
import com.horizonglitch.wotv.common.Paging;
import com.horizonglitch.wotv.notification.NotificationService;
import com.horizonglitch.wotv.notification.NotificationType;
import com.horizonglitch.wotv.player.Player;
import com.horizonglitch.wotv.player.PlayerRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendshipService {
	private static final int MAX_FRIENDS = 100;
	private static final int MAX_PENDING_FRIEND_REQUESTS = 10;

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
	public PageResponse<FriendshipResponses.Friend> friends(long playerId, Integer page, Integer size) {
		activePlayer(playerId);
		return PageResponse.of(friendships.findAccepted(playerId, Paging.of(page, size, newestFirst())),
				friendship -> friendView(friendship, playerId));
	}

	@Transactional(readOnly = true)
	public PageResponse<FriendshipResponses.Request> requests(long playerId, String direction,
			Integer page, Integer size) {
		activePlayer(playerId);
		if (!direction.equals("incoming") && !direction.equals("outgoing")) {
			throw ApiException.validation(List.of(new ApiError.FieldErrorItem("direction", "DIRECTION_INVALID")));
		}
		return PageResponse.of(friendships.findPending(playerId, direction, Paging.of(page, size, newestFirst())),
				friendship -> requestView(friendship, playerId));
	}

	@Transactional
	public FriendshipResponses.Request send(long requesterId, FriendshipRequests.Create request) {
		Player requester = players.lockById(requesterId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "The account no longer exists."));
		Player addressee = players.findActiveByUsername(request.username().trim())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAYER_NOT_FOUND", "The player does not exist."));
		if (requesterId == addressee.getId()) {
			throw new ApiException(HttpStatus.CONFLICT, "FRIENDSHIP_SELF_REQUEST", "You cannot send a friend request to yourself.");
		}
		if (friendships.existsBetween(requesterId, addressee.getId())) {
			throw alreadyExists();
		}
		if (friendships.countFriends(requesterId) >= MAX_FRIENDS) {
			throw new ApiException(HttpStatus.CONFLICT, "FRIEND_LIMIT_REACHED",
					"A player can have at most " + MAX_FRIENDS + " friends.");
		}
		if (friendships.countByRequester_IdAndStatus(requesterId, FriendshipStatus.PENDING) >= MAX_PENDING_FRIEND_REQUESTS) {
			throw new ApiException(HttpStatus.CONFLICT, "FRIEND_REQUEST_LIMIT_REACHED",
					"You can have at most " + MAX_PENDING_FRIEND_REQUESTS + " pending sent requests.");
		}
		Friendship friendship = new Friendship();
		friendship.setRequester(requester);
		friendship.setAddressee(addressee);
		try {
			friendship = friendships.saveAndFlush(friendship);
		} catch (DataIntegrityViolationException exception) {
			if (String.valueOf(exception.getMostSpecificCause().getMessage()).contains("uq_friendship_pair")) {
				throw alreadyExists();
			}
			throw exception;
		}
		notifications.notifyFriendship(addressee, NotificationType.FRIEND_REQUEST_RECEIVED, friendship);
		return requestView(friendship, requesterId);
	}

	@Transactional
	public FriendshipResponses.Friend accept(long playerId, long friendshipId) {
		activePlayer(playerId);
		Friendship friendship = loadLocked(friendshipId);
		if (!friendship.getAddressee().getId().equals(playerId)) {
			throw forbidden();
		}
		if (friendship.getStatus() != FriendshipStatus.PENDING) {
			throw notPending();
		}
		List<Player> participants = players.lockActiveByIds(List.of(
				friendship.getRequester().getId(), friendship.getAddressee().getId()));
		if (participants.size() != 2) {
			throw new ApiException(HttpStatus.NOT_FOUND, "PLAYER_NOT_FOUND", "One of the players does not exist.");
		}
		if (friendships.countFriends(playerId) >= MAX_FRIENDS
				|| friendships.countFriends(friendship.getRequester().getId()) >= MAX_FRIENDS) {
			throw new ApiException(HttpStatus.CONFLICT, "FRIEND_LIMIT_REACHED",
					"One of the players already has " + MAX_FRIENDS + " friends.");
		}
		friendship.setStatus(FriendshipStatus.ACCEPTED);
		friendship.setAcceptedAt(clock.instant());
		notifications.notifyFriendship(friendship.getRequester(), NotificationType.FRIEND_REQUEST_ACCEPTED, friendship);
		return friendView(friendship, playerId);
	}

	@Transactional
	public void reject(long playerId, long friendshipId) {
		activePlayer(playerId);
		Friendship friendship = loadLocked(friendshipId);
		if (!friendship.getAddressee().getId().equals(playerId)) {
			throw forbidden();
		}
		if (friendship.getStatus() != FriendshipStatus.PENDING) {
			throw notPending();
		}
		friendships.delete(friendship);
	}

	@Transactional
	public void remove(long playerId, long friendshipId) {
		activePlayer(playerId);
		Friendship friendship = loadLocked(friendshipId);
		boolean requester = friendship.getRequester().getId().equals(playerId);
		boolean addressee = friendship.getAddressee().getId().equals(playerId);
		if (!requester && !addressee) {
			throw forbidden();
		}
		if (friendship.getStatus() == FriendshipStatus.PENDING && !requester) {
			throw forbidden();
		}
		friendships.delete(friendship);
	}

	private Player activePlayer(long id) {
		return players.findByIdAndDeletedAtIsNull(id)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "The account no longer exists."));
	}

	private Friendship loadLocked(long id) {
		return friendships.lockById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
				"FRIENDSHIP_NOT_FOUND", "The friendship does not exist."));
	}

	private static FriendshipResponses.Friend friendView(Friendship friendship, long viewerId) {
		Player other = friendship.getRequester().getId() == viewerId
				? friendship.getAddressee() : friendship.getRequester();
		return new FriendshipResponses.Friend(friendship.getId(), other.getId(), other.getUsername(),
				other.getAvatar() == null ? null : other.getAvatar().getName(), other.getTrophies(),
				friendship.getAcceptedAt());
	}

	private static FriendshipResponses.Request requestView(Friendship friendship, long viewerId) {
		boolean outgoing = friendship.getRequester().getId() == viewerId;
		Player other = outgoing ? friendship.getAddressee() : friendship.getRequester();
		return new FriendshipResponses.Request(friendship.getId(), other.getId(), other.getUsername(),
				other.getAvatar() == null ? null : other.getAvatar().getName(), friendship.getCreatedAt(),
				outgoing ? "outgoing" : "incoming");
	}

	private static Sort newestFirst() {
		return Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
	}

	private static ApiException alreadyExists() {
		return new ApiException(HttpStatus.CONFLICT, "FRIENDSHIP_ALREADY_EXISTS", "A relationship between these players already exists.");
	}

	private static ApiException forbidden() {
		return new ApiException(HttpStatus.FORBIDDEN, "FRIENDSHIP_FORBIDDEN", "You cannot perform this action on the friendship.");
	}

	private static ApiException notPending() {
		return new ApiException(HttpStatus.CONFLICT, "FRIEND_REQUEST_NOT_PENDING", "The friend request is no longer pending.");
	}
}
