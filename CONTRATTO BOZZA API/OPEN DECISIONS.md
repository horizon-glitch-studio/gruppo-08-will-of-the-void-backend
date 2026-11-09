# Open Decisions — Draft API Contract

This document collects the open questions raised by the area contracts. Confirmed decisions are omitted. When a decision is made, update both this list and the relevant area contract.

## Authentication and profile

Reference: [01-auth-profilo.md](01-auth-profilo.md)

- [ ] **Email verification:** Login is allowed before email verification. Which features should remain unavailable until verification (for example, Ranked matches or pack purchases)?
- [ ] **Avatars:** Define the set/version of preset avatars and add the `avatar` field to the schema through a migration.
- [ ] **Leagues:** Define league names and trophy thresholds; the league is calculated from trophies.
- [ ] **Rate limiting — phase 2:** Define thresholds and implementation. Do not include `429 TOO_MANY_REQUESTS` in the v1 contract for authentication endpoints.

## Catalog, collection, and decks

Reference: [02-carte-collezione-mazzi.md](02-carte-collezione-mazzi.md)

- [ ] **Inactive cards:** Confirm that the player-facing contract exposes only active cards, and decide whether administrators get a separate endpoint for inactive cards.
- [ ] **Lists and search:** Define sorting, filters, and pagination for the catalog, collection, and decks.
- [ ] **Deck limits:** Define the maximum number of decks per player and the allowed deck name length.
- [ ] **Saving invalid decks:** Confirm that incomplete/invalid decks can be saved; the model defines `INCOMPLETE`, `VALID`, and `INVALID` states. Only valid decks can be used in battles.
- [ ] **Defense deck:** Decide whether the defense deck can be deleted without selecting a replacement first.

## Packs and credits

Reference: [03-pacchetti-crediti.md](03-pacchetti-crediti.md)

- [ ] **Odds:** Decide whether pack odds are exposed to players and how rarity upgrades are represented.
- [ ] **Pack offering:** Define pack quantity, availability, event windows, and any purchase limits.
- [ ] **History:** Define the format, sorting, and retention period for pack openings and credit transactions.
- [ ] **Daily bonus:** The bonus is granted at login. Decide whether to also provide an endpoint for claiming it manually.

## Trades

Reference: [04-scambi.md](04-scambi.md)

- [ ] **Trade composition:** Define the minimum and maximum number of cards/lines per trade, and confirm that trading with oneself is forbidden.
- [ ] **Card ownership:** The project context specifies checking ownership when the trade is accepted. Confirm whether to keep that behavior or require ownership when proposing the trade.
- [ ] **Duplicate inputs and recipient:** Define how duplicate cards in a request and a nonexistent recipient username are handled.
- [ ] **Lists and notifications:** Define filters/pagination and which status changes generate notifications.

## Battles and matchmaking

Reference: [05-battaglie-matchmaking.md](05-battaglie-matchmaking.md)

- [ ] **Matchmaking:** Define the trophy range and the algorithm for selecting and refreshing the opponent list, including its cooldown.
- [ ] **Ranking:** Define the Elo formula, draw handling, and the duration/rules for protection (`protectedUntil`).
- [ ] **Gameplay actions:** Define the action schema, server authority, reconnection behavior, and idempotency.
- [ ] **Replays:** Define who can access them, how long they are retained, and when logs become visible.
- [ ] **Real-time transport:** Choose WebSocket/STOMP or an alternative, and define events and payloads.

## Friendships, notifications, and presence

Reference: [06-social-notifiche.md](06-social-notifiche.md)

- [ ] **Friend requests:** Decide whether requests can be sent to offline users and set any friend limit.
- [ ] **Accepting a request:** Clarify whether accepting a request also starts a challenge or only creates the friendship.
- [ ] **Presence:** Define visibility (for example, friends only), heartbeat, and the timeout used to mark a user offline.
- [ ] **Notifications:** Define retention, sorting, limits, and which events are pushed as well as persisted.
- [ ] **Removing a relationship:** Define how `DELETE /friends/{friendshipId}` works for accepted friendships and outgoing requests; incoming requests are rejected through the dedicated endpoint.

## Cross-cutting conventions and scope

Reference: [README.md](README.md)

- [ ] Define common pagination, sorting, and filtering behavior to keep it consistent across areas.
- [ ] Consolidate the catalog of area-specific error codes and their HTTP status codes.
- [ ] Decide which endpoints require a verified email and which are available without login, consistently with the decisions above.
- [ ] Write the admin contract for managing cards, rarities, packs, and the starter deck.
- [ ] Write the WebSocket contract for presence and real-time challenges.
