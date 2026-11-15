# Open Decisions — Draft API Contract

This document collects the open questions raised by the area contracts. Confirmed decisions are omitted. When a decision is made, update both this list and the relevant area contract.

## Authentication and profile

Reference: [01-auth-profile.md](01-auth-profile.md)

- [x] **Email verification:** Login is allowed before email verification. Which features should remain unavailable until verification?

      proposal: require a verified email for trades and friend invitation campaign.
- [ ] **Leagues:** Define league names.

      proposal: check with Marco whether the names have already been defined for lore purposes
- [x] **Rate limiting — phase 2:** Define thresholds and implementation. Do not include `429 TOO_MANY_REQUESTS` in the v1 contract for authentication endpoints.

      proposal: defer limits and implementation to v2
## Catalog, collection, and decks

Reference: [02-cards-collection-decks.md](02-cards-collection-decks.md)

- [ ] **Inactive cards:** Does the player get to see inactive cards? then decide whether administrators get a separate endpoint for inactive cards.

      proposal: no; manage inactive cards through separate admin endpoints. Cards already owned may remain visible in the collection.
- [ ] **Lists and search:** Define sorting, filters, and pagination for the catalog, collection, and decks.

      proposal (from codex): use the common pagination (`page` starting at 0, default `size` 20 and maximum 100), stable ID sorting, and catalog filters for faction, rarity, and name search; collection filters for faction, rarity, and availability in a deck.
      
- [ ] **Deck limits:** Define the maximum number of decks per player and the allowed deck name length.

      proposal: allow up to 20 decks per player and names up to 50 characters.
- [x] **Saving invalid decks:** Confirm that incomplete/invalid decks can be saved; the model defines `INCOMPLETE`, `VALID`, and `INVALID` states. Only valid decks can be used in battles.

      proposal: allow incomplete or invalid decks to be saved so players can edit them incrementally; only `VALID` decks can be used in battle or set as the defense deck.
## Packs and credits

Reference: [03-packs-credits.md](03-packs-credits.md)

- [x] **Odds:** Decide whether pack odds are exposed to players and how rarity upgrades are represented.

      proposal: show the odds of every slot and the guarantees (Champion or higher within 10 packs, Elect within 50 standard packs, Wanderer within 50 event packs).
- [ ] **Pack offering:** Define pack purchase limits.

      proposal: always available
- [ ] **History:** Define sorting and retention period of both battle and transactions for bought in game content like packs and credits.

      proposal: sort newest first; retain opening and transaction history for the lifetime of the account, unless different retention requirements apply.
- [x] **Daily bonus:** The bonus is granted at login. Decide whether to also provide an endpoint for claiming it manually.

      proposal: the bonus is granted automatically at the first login of the day, no manual claim.

## Trades


- [x] **Card ownership:** The project context specifies checking ownership when the trade is accepted. Confirm whether to keep that behavior or require ownership when proposing the trade.

      proposal: check ownership only when the trade is accepted; the same copy can be offered in several pending trades; if the cards are no longer available, close the trade as `FAILED` without partial transfers.
- [ ] **recipient:** Define how a nonexistent recipient username is handled.

      proposal: normalize usernames case-insensitively and return `PLAYER_NOT_FOUND` for nonexistent recipients.
- [ ] **notifications:** Define which status changes generate notifications.

      proposal: notify the other party when a trade is received and whenever it reaches a final status.

## Battles and matchmaking


- [ ] **Replays:** Define who can access them, how long they are retained, and when logs become visible.

      proposal: replays are available only to participants after the match ends; retain them for 90 days.
- [ ] **Leaderboard and seasons:** Define the routes for the leaderboard (global and per orbit, listing only players with at least one RANKED battle in the current season) and for the current season.

      proposal: `GET /leaderboard` (paginated, optional `orbit` filter) and `GET /seasons/current` (start and end dates).
- [x] **Matchmaking:** Define the trophy range and the algorithm for selecting and refreshing the opponent list, including its cooldown.

      proposal: 5 opponents within a trophy range set by the player's orbit (0–199 ±200, 200–499 ±150, 500–999 ±100, 1000+ ±50), doubled until the list is full; QUICK lists random players active in the last 7 days; refresh at most every 5 seconds.
- [x] **Ranking:** Define the duration/rules for protection (`protectedUntil`).

      proposal: shield: 5 RANKED attacks received in 24 hours give 12 hours of protection; it ends at once if the player attacks in RANKED.
- [x] **Gameplay actions:** Define the action schema, server authority, reconnection behavior, and idempotency.

      proposal: no actions: the server simulates the whole match at once.
- [x] **Real-time transport:** Choose WebSocket/STOMP or an alternative, and define events and payloads.

      proposal: no websockets at all

## Friendships, notifications, and presence


- [ ] **Friend requests:** Decide whether requests can be sent to offline users and set any friend limit.

      proposal: allow requests to offline users and store them for later delivery; set a limit of 100 friends and 10 pending outgoing requests per player.
- [ ] **Presence:** Define visibility (for example, friends only), heartbeat, and the timeout used to mark a user offline.

      proposal: show presence only to accepted friends; send a heartbeat every 30 seconds and mark the player offline after 90 seconds without one.
- [ ] **Notifications:** Define retention, sorting, limits, and which events are pushed as well as persisted.

      proposal (from codex): use the common pagination and newest-first sorting; retain notifications for 90 days, with a maximum of 500 per user, deleting the oldest read notifications first. Persist events; instead of real-time delivery, the client polls `GET /users/me/notifications/unread-count` at a fixed interval (for example every 30 seconds) and while the app is open.
- [ ] **Removing a relationship:** Define how `DELETE /friends/{friendshipId}` works for accepted friendships and outgoing requests; incoming requests are rejected through the dedicated endpoint.

      proposal: use dedicated separated endpoints for rejecting new requests and removing already existing friends