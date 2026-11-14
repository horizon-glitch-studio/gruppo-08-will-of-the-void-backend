# API Contract: Friendships, Notifications, and Presence

Prefix: `/api/v1`. Based on `friendship` and `notification`. Online presence is volatile and is kept in memory rather than in the database; since there is no WebSocket, clients update it through periodic REST requests (see open decisions).

## Friendships

| Method | Route | Auth | Result |
|---|---|---|---|
| GET | `/friends` | yes | accepted friendships |
| GET | `/friend-requests` | yes | received and sent requests, filterable by `direction` |
| POST | `/friend-requests` | yes | sends a request with `{ "username": "star_hunter" }`, 201 |
| POST | `/friend-requests/{friendshipId}/accept` | yes | accepts a received request |
| POST | `/friend-requests/{friendshipId}/reject` | yes | rejects and removes a request |
| DELETE | `/friends/{friendshipId}` | yes | removes an accepted friendship or cancels a sent request |

The same pair of players cannot have two relationships, even if requester/addressee are reversed. Players cannot send requests to themselves. Removing a relationship deletes it; a new request can be sent later.

## Notifications

| Method | Route | Auth | Result |
|---|---|---|---|
| GET | `/users/me/notifications` | yes | paginated list; filters `unreadOnly` and `type` |
| GET | `/users/me/notifications/unread-count` | yes | `{ "count": 3 }` |
| PATCH | `/users/me/notifications/{notificationId}` | yes | `{ "isRead": true }` |
| POST | `/users/me/notifications/read-all` | yes | marks all as read, 204 |
| DELETE | `/users/me/notifications/{notificationId}` | yes | deletes a notification |

Types defined by the schema: `ATTACK_RECEIVED`, `TRADE_RECEIVED`, `TRADE_ACCEPTED`, `TRADE_REJECTED`, `TRADE_CANCELLED`, `TRADE_EXPIRED`, `TRADE_FAILED`, `WANDERER_DEPARTED`, `DECK_INVALID`, `INVITE_BONUS`, `COMEBACK_BONUS`, `SEASON_REWARD`, `FRIEND_REQUEST_RECEIVED`, `FRIEND_REQUEST_ACCEPTED`, `CHALLENGE_RECEIVED`, `CHALLENGE_ACCEPTED`, `CHALLENGE_REJECTED`, `CHALLENGE_EXPIRED`, `CHALLENGE_CANCELLED`. Proposed response object: `id`, `type`, `isRead`, `createdAt`, optional references `battle`, `trade`, `card`, `friendship`; the client retrieves details shown to the player from the relevant route rather than from server-localized text.

Proposed specific errors: `FRIENDSHIP_NOT_FOUND` 404, `FRIENDSHIP_FORBIDDEN` 403, `FRIENDSHIP_ALREADY_EXISTS` 409, `FRIEND_REQUEST_NOT_PENDING` 409, `NOTIFICATION_NOT_FOUND` 404.

## To decide

- Whether friend requests are allowed when the recipient is offline and whether there is a friend limit.
- Whether accepting a request starts a challenge immediately or only adds the friendship.
- Presence visibility (friends only) and heartbeat/offline timeout.
- Notification retention, sorting, and limits; which events are pushed as well as persisted.
- Whether `DELETE /friends/{id}` distinguishes accepted friendships from sent requests (incoming requests should use reject).
