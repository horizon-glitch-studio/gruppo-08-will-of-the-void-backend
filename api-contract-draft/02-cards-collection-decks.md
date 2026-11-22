# API Contract: Catalog, Collection, and Decks

Prefix: `/api/v1`. Models are based on `card`, `faction`, `rarity`, `collection_card`, `memory_archive`, `deck`, and `deck_card`.

## Card model

The exposed card includes `id`, `name`, `description`, `imageUrl`, `faction`, `rarity`, `attack`, and `defense`. The public catalog returns active cards only; inactive cards are managed through the admin endpoints. Copies of an inactive card already owned stay in the collection and can still be used in decks and battles. `faction` contains an ID and name; `rarity` contains a name, order, and copy limits relevant to the client. Pack odds are not part of the card. `imageUrl` is the path of the image without width and extension: the server publishes it in three widths, `<imageUrl>-320w.webp`, `<imageUrl>-640w.webp` and `<imageUrl>-848w.webp`, so the client can build a `srcset` such as `/cards/lava/basaltaur-320w.webp 320w, /cards/lava/basaltaur-640w.webp 640w, /cards/lava/basaltaur-848w.webp 848w`. The images are public and need no token.

## Catalog

| Method | Route | Auth | Result |
|---|---|---|---|
| GET | `/cards` | no | paginated list; proposed filters: `factionId`, `rarityId`, `search`, `sort` |
| GET | `/cards/{cardId}` | no | active card details, 404 `CARD_NOT_FOUND` |
| GET | `/game-data` | no | reference data: factions, rarities, and faction advantages |

Faction advantages affect attack only: the dominant faction gets an ATK bonus. The catalog exposes the advantage pairs and value without duplicating the server's calculation logic.

## Player collection

| Method | Route | Auth | Result |
|---|---|---|---|
| GET | `/users/me/collection` | yes | paginated collection; filters: `factionId`, `rarityId`, `availableInDeck` |
| GET | `/users/me/collection/{cardId}` | yes | owned quantity, acquisition/expiration dates, and card |
| GET | `/users/me/archive` | yes | paginated archive of departed Wanderers |

Example collection item:

```json
{
  "card": { "id": 42, "name": "Cetargos", "imageUrl": "/cards/noble-gas/cetargos", "faction": { "id": 8, "name": "Noble Gas" }, "rarity": { "id": 6, "name": "Wanderer" }, "attack": 18, "defense": 14 },
  "quantity": 1,
  "acquiredAt": "2026-11-08T16:00:00Z",
  "expiresAt": "2026-11-15T16:00:00Z"
}
```

`expiresAt: null` means the card is permanent. A Wanderer has a quantity of one; expiration and archiving are managed by the server and cannot be changed through the API.

## Decks

| Method | Route | Auth | Result |
|---|---|---|---|
| GET | `/users/me/decks` | yes | player's decks |
| POST | `/users/me/decks` | yes | creates a deck, 201 |
| GET | `/users/me/decks/{deckId}` | yes | details |
| PUT | `/users/me/decks/{deckId}` | yes | replaces name and card list |
| DELETE | `/users/me/decks/{deckId}` | yes | 204 |
| PUT | `/users/me/decks/{deckId}/defense` | yes | sets the defensive deck |
| DELETE | `/users/me/decks/{deckId}/defense` | yes | removes the defensive deck selection |
| POST | `/users/me/decks/validate` | yes | validates a composition without saving it |

Create/replace:

```json
{
  "name": "Solar control",
  "cards": [{ "cardId": 42, "quantity": 1 }, { "cardId": 51, "quantity": 3 }]
}
```

Deck response: `id`, `name`, `status` (`INCOMPLETE`, `VALID`, `INVALID`), `cards` with card and quantity, `totalCards`, `isDefenseDeck`, `updatedAt`, `validationErrors`.

Constraints: a valid deck has 30 cards, with copy limits per card and per rarity and a maximum of 15 cards per faction; quantities cannot exceed the player's collection. Apply current limits after trades and Wanderer expiration as well.

A player can save up to 20 decks; deck names have at most 100 characters. The same card cannot appear twice in `cards`: the request is rejected with 400 `VALIDATION_FAILED`.

Proposed specific errors: `DECK_NOT_FOUND` 404, `DECK_LIMIT_REACHED` 409, `DECK_INVALID` 422, `CARD_NOT_OWNED` 409, `DECK_NAME_ALREADY_USED` 409, `DEFENSE_DECK_INVALID` 409, `CARD_NOT_FOUND` 404. Validation responses should report errors per card/field rather than a single string.

## Implemented behaviour

- **Catalog.** `sort` is `id` (default), `name`, `attack`, `defense` or `rarity`; another value is 400 `VALIDATION_FAILED` with `SORT_INVALID`. `search` matches part of the name, ignoring case. Rarity points are never exposed. `/game-data` also returns `deckRules` (`deckSize`, `maxPerElementalFaction`, `maxSavedDecks`).
- **Pagination errors.** `page` below 0 or `size` outside 1–100 is 400 `VALIDATION_FAILED` with `PAGE_INVALID` or `SIZE_INVALID`.
- **Collection.** `availableInDeck=true` leaves out Wanderers whose time has ended, even before they are moved to the archive. `GET /users/me/collection/{cardId}` is 404 `CARD_NOT_FOUND` for a card not owned. The archive is sorted by departure, most recent first.
- **Saving a deck.** A card that does not exist is 404 `CARD_NOT_FOUND`. A card not owned, or used in more copies than owned, is 409 `CARD_NOT_OWNED` with one `fieldErrors` item per card (`cards[i]`). Other `fieldErrors` codes: `CARD_DUPLICATED`, `QUANTITY_INVALID`, `DECK_NAME_TOO_LONG`, `FIELD_REQUIRED`.
- **Deck status.** A deck that breaks a rule is saved anyway, with status `INVALID` (or `INCOMPLETE` when it only has fewer than 30 cards), so drafts can be kept. The status is recomputed every time the deck is saved or read, so it follows trades and departed Wanderers.
- **`validationErrors`.** Each item is `{ "code", "cardId", "rarityId", "factionId", "limit", "actual" }`, with only the ids that apply. Codes: `DECK_SIZE`, `TOO_MANY_COPIES`, `RARITY_LIMIT`, `FACTION_LIMIT` (elemental factions only), `NOT_ENOUGH_COPIES`, `CARD_NOT_PLAYABLE` (a Wanderer whose time has ended).
- **Defense deck.** `PUT /{deckId}/defense` returns the deck (200) and is 409 `DEFENSE_DECK_INVALID` unless the deck is `VALID`. A defense deck that later becomes invalid stays selected but cannot defend. Deleting it leaves the player without a defense deck.
- **`POST /users/me/decks/validate`** takes `{ "cards": [...] }` and returns `status`, `totalCards` and `validationErrors`; cards not owned are reported as `NOT_ENOUGH_COPIES` instead of being refused.
