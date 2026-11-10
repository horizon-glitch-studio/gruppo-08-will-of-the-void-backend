# Open Decisions — Draft API Contract

This document collects the open questions raised by the area contracts. Confirmed decisions are omitted. When a decision is made, update both this list and the relevant area contract.

## Authentication and profile

Reference: [01-auth-profile.md](01-auth-profile.md)

- [ ] **Email verification:** Login is allowed before email verification. Which features should remain unavailable until verification?

      **Proposed solution:** allow login and gameplay, but require a verified email for trades and friend invitations.
- [ ] **Leagues:** Define league names and trophy thresholds; the league is calculated from trophies.

      **Proposed solution:** check with Marco whether the names and thresholds have already been defined; otherwise, use 5 leagues (`ORBIT_1`–`ORBIT_5`) with server-configurable thresholds.
- [ ] **Rate limiting — phase 2:** Define thresholds and implementation. Do not include `429 TOO_MANY_REQUESTS` in the v1 contract for authentication endpoints.

      **Proposed solution:** defer limits and implementation to v2; do not document `429 TOO_MANY_REQUESTS` for authentication endpoints in v1.
## Catalog, collection, and decks

Reference: [02-cards-collection-decks.md](02-cards-collection-decks.md)

- [ ] **Inactive cards:** Confirm that the player-facing contract exposes only active cards, and decide whether administrators get a separate endpoint for inactive cards.

      **Proposed solution:** expose only active cards in the player-facing catalog; manage inactive cards through separate admin endpoints. Cards already owned may remain visible in the collection.
- [ ] **Lists and search:** Define sorting, filters, and pagination for the catalog, collection, and decks.

      **Proposed solution:** use the common pagination (`page` starting at 0, default `size` 20 and maximum 100), stable ID sorting, and catalog filters for faction, rarity, and name search; collection filters for faction, rarity, and availability in a deck.
- [ ] **Deck limits:** Define the maximum number of decks per player and the allowed deck name length.

      **Proposed solution:** allow up to 10 decks per player and names from 1 to 30 characters after trimming leading and trailing spaces.
- [ ] **Saving invalid decks:** Confirm that incomplete/invalid decks can be saved; the model defines `INCOMPLETE`, `VALID`, and `INVALID` states. Only valid decks can be used in battles.

      **Proposed solution:** allow incomplete or invalid decks to be saved so players can edit them incrementally; only `VALID` decks can be used in battle or set as the defense deck.
- [ ] **Defense deck:** Decide whether the defense deck can be deleted without selecting a replacement first.

      **Proposed solution:** allow the defense deck to be removed without a replacement and set `defenseDeckId` to `null`; require a valid defense deck only in modes that need one.
## Packs and credits

Reference: [03-packs-credits.md](03-packs-credits.md)

- [ ] **Odds:** Decide whether pack odds are exposed to players and how rarity upgrades are represented.

      **Proposed solution:** show the final odds by rarity for each pack, including odds after upgrades, without exposing slot configuration or random seeds.
- [ ] **Pack offering:** Define pack quantity, availability, event windows, and any purchase limits.

      **Proposed solution:** list only packs currently available for purchase and include their availability period and any configured limits; enforce event limits on the server.
- [ ] **History:** Define the format, sorting, and retention period for pack openings and credit transactions.

      **Proposed solution:** use the common pagination and sort newest first; retain opening and transaction history for the lifetime of the account, unless different retention requirements apply.
- [ ] **Daily bonus:** The bonus is granted at login. Decide whether to also provide an endpoint for claiming it manually.

      **Proposed solution:** do not add a claim endpoint in v1; grant the bonus at login and return the result in the login response.

## Trades

Reference: [04-trades.md](04-trades.md)

- [ ] **Trade composition:** Define the minimum and maximum number of cards/lines per trade, and confirm that trading with oneself is forbidden.

      **Proposed solution:** require at least one card on each side, with a maximum of 5 lines and 10 copies per side; prohibit trading with oneself and trading Wanderers.
- [ ] **Card ownership:** The project context specifies checking ownership when the trade is accepted. Confirm whether to keep that behavior or require ownership when proposing the trade.

      **Proposed solution:** check ownership when the trade is accepted and lock quantities during the transfer; if they are no longer available, close the trade as `FAILED` without partial transfers.
- [ ] **Duplicate inputs and recipient:** Define how duplicate cards in a request and a nonexistent recipient username are handled.

      **Proposed solution:** reject duplicate card IDs in the same request; normalize usernames case-insensitively and return `PLAYER_NOT_FOUND` for nonexistent recipients.
- [ ] **Lists and notifications:** Define filters/pagination and which status changes generate notifications.

      **Proposed solution:** use the common pagination, `status` and `direction` filters, and newest-first sorting; notify the other party when a trade is received and whenever it reaches a final status.

## Battles and matchmaking

Reference: [05-battles-matchmaking.md](05-battles-matchmaking.md)

- [ ] **Matchmaking:** Define the trophy range and the algorithm for selecting and refreshing the opponent list, including its cooldown.

      **Proposed solution:** suggest 5 opponents within ±200 trophies, widening the range if needed; exclude the player and opponents already faced from the current list. Keep the list until it is refreshed and apply a 60-second cooldown.
- [ ] **Ranking:** Define the Elo formula, draw handling, and the duration/rules for protection (`protectedUntil`).

      **Proposed solution:** use Elo with K=32, scoring 1 for a win, 0 for a loss, and 0.5 for a draw; provide protection for 24 hours after a loss. Confirm the values and balance before finalizing them in the contract.
- [ ] **Gameplay actions:** Define the action schema, server authority, reconnection behavior, and idempotency.

      **Proposed solution:** make the server authoritative for turns, legal moves, and outcomes; identify each action with a UUID and turn number, return the original result on retry, and allow state recovery after reconnection.
- [ ] **Replays:** Define who can access them, how long they are retained, and when logs become visible.

      **Proposed solution:** make replays available only to participants after the match ends and retain them for 90 days; do not reveal the opponent's hidden information during a match.
- [ ] **Real-time transport:** Choose WebSocket/STOMP or an alternative, and define events and payloads.

      **Proposed solution:** use WebSocket with STOMP for live events; keep REST for submitting actions, reading state, and recovery after reconnection.

## Friendships, notifications, and presence

Reference: [06-social-notifications.md](06-social-notifications.md)

- [ ] **Friend requests:** Decide whether requests can be sent to offline users and set any friend limit.

      **Proposed solution:** allow requests to offline users and store them for later delivery; set a limit of 100 friends and 20 pending outgoing requests per player.
- [ ] **Accepting a request:** Clarify whether accepting a request also starts a challenge or only creates the friendship.

      **Proposed solution:** accepting a request only creates the friendship; a challenge must be started separately.
- [ ] **Presence:** Define visibility (for example, friends only), heartbeat, and the timeout used to mark a user offline.

      **Proposed solution:** show presence only to accepted friends; send a heartbeat every 30 seconds and mark the player offline after 90 seconds without one.
- [ ] **Notifications:** Define retention, sorting, limits, and which events are pushed as well as persisted.

      **Proposed solution:** use the common pagination and newest-first sorting; retain notifications for 90 days, with a maximum of 500 per user, deleting the oldest read notifications first. Persist events and send them in real time when the recipient is online.
- [ ] **Removing a relationship:** Define how `DELETE /friends/{friendshipId}` works for accepted friendships and outgoing requests; incoming requests are rejected through the dedicated endpoint.

      **Proposed solution:** allow an accepted friendship to be removed or an outgoing request to be cancelled; use the dedicated rejection endpoint for incoming requests.
