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

- [ ] **Odds:** Decide whether pack odds are exposed to players and how rarity upgrades are represented.

      proposal: leave it for phase 2 if needed otherwise: show the odds by rarity for packs, including odds after upgrades (e.g. making sure a Legendary card gets pulled every 50 packs), without exposing slot configuration or random seeds.
      example (brawl stars chest): https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQIExO1rGXbW8Tbd3w4YI6tnsFGnQKXYiITJtRnkCLC49FHObpyORiJdpM&s=10
      
- [ ] **Pack offering:** Define pack purchase limits.

      proposal: always available
- [ ] **History:** Define sorting and retention period of both battle and transactions for bought in game content like packs and credits.

      proposal: sort newest first; retain opening and transaction history for the lifetime of the account, unless different retention requirements apply.
- [ ] **Daily bonus:** The bonus is granted at login. Decide whether to also provide an endpoint for claiming it manually.

      proposal: grant the bonus at login with a dedicated page so the user has to redeem it.
      example: https://imgur.com/a/f0tI8a7

## Trades


- [ ] **Card ownership:** The project context specifies checking ownership when the trade is accepted. Confirm whether to keep that behavior or require ownership when proposing the trade.

      proposal: check ownership both when proposing and when the trade is accepted and lock quantities during the transfer; if they are no longer available, close the trade as `FAILED` without partial transfers.
- [ ] **recipient:** Define how a nonexistent recipient username is handled.

      proposal: normalize usernames case-insensitively and return `PLAYER_NOT_FOUND` for nonexistent recipients.
- [ ] **notifications:** Define which status changes generate notifications.

      proposal: notify the other party when a trade is received and whenever it reaches a final status.

## Battles and matchmaking


- [ ] **Matchmaking:** Define the trophy range and the algorithm for selecting and refreshing the opponent list, including its cooldown.

      proposal: suggest 5 opponents within ±50 trophies, widening the range if needed; exclude the player and opponents already faced from the current list. Keep the list until it is refreshed and apply a 60-second cooldown.
- [ ] **Ranking:** Define the duration/rules for protection (`protectedUntil`).

      proposal:  provide protection for 6 hours after a loss.
- [ ] **Gameplay actions:** Define the action schema, server authority, reconnection behavior, and idempotency.

      proposal: make the server authoritative for turns, legal moves, and outcomes; identify each action with a UUID and turn number, return the original result on retry, and allow state recovery after reconnection.
- [ ] **Replays:** Define who can access them, how long they are retained, and when logs become visible.

      proposal: make replays available only to participants after the match ends and retain them for 90 days; should be visible in a dedicated tab as the ongoing match is playing out (look at Duel Logs on the right for reference -> https://imgur.com/a/OPrvN7k), also not mandatory so it could go for phase 2 if needed.
- [x] **Real-time transport:** Choose WebSocket/STOMP or an alternative, and define events and payloads.

      proposal: no websockets at all

## Friendships, notifications, and presence


- [ ] **Friend requests:** Decide whether requests can be sent to offline users and set any friend limit.

      proposal: allow requests to offline users and store them for later delivery; set a limit of 100 friends and 10 pending outgoing requests per player.
- [ ] **Presence:** Define visibility (for example, friends only), heartbeat, and the timeout used to mark a user offline.

      proposal: show presence only to accepted friends; send a heartbeat every 30 seconds and mark the player offline after 90 seconds without one.
- [ ] **Notifications:** Define retention, sorting, limits, and which events are pushed as well as persisted.

      proposal (from codex): use the common pagination and newest-first sorting; retain notifications for 90 days, with a maximum of 500 per user, deleting the oldest read notifications first. Persist events and send them in real time when the recipient is online.
- [ ] **Removing a relationship:** Define how `DELETE /friends/{friendshipId}` works for accepted friendships and outgoing requests; incoming requests are rejected through the dedicated endpoint.

      proposal: use dedicated separated endpoints for rejecting new requests and removing already existing friends