# API Contract: Catalog, Collection, and Decks

Prefix: `/api/v1`. Models are based on `card`, `faction`, `rarity`, `collection_card`, `memory_archive`, `deck`, and `deck_card`.

## Card model

The exposed card includes `id`, `name`, `description`, `imageUrl`, `faction`, `rarity`, `attack`, and `defense`. The public catalog returns active cards only. `faction` contains an ID and name; `rarity` contains a name, order, and copy limits relevant to the client. Pack odds are not part of the card.

## Catalog

| Method | Route | Auth | Result |
|---|---|---|---|
| GET | `/cards` | no | paginated list; proposed filters: `factionId`, `rarityId`, `search`, `sort` |
| GET | `/cards/{cardId}` | no | active card details, 404 `CARD_NOT_FOUND` |
| GET | `/game-data` | no | reference data: factions, rarities, and faction advantages |

Faction advantages can affect attack or defense. The catalog should expose the value and direction (`ATTACK`/`DEFENSE`) without duplicating the server's calculation logic.

## Player collection

| Method | Route | Auth | Result |
|---|---|---|---|
| GET | `/users/me/collection` | yes | paginated collection; filters: `factionId`, `rarityId`, `availableInDeck` |
| GET | `/users/me/collection/{cardId}` | yes | owned quantity, acquisition/expiration dates, and card |
| GET | `/users/me/archive` | yes | paginated archive of departed Wanderers |

Example collection item:

```json
{
  "card": { "id": 42, "name": "Aster", "imageUrl": "/cards/42.png", "faction": { "id": 2, "name": "Solar" }, "rarity": { "id": 4, "name": "Wanderer" }, "attack": 8, "defense": 6 },
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
| DELETE | `/users/me/decks/{deckId}/defense` | yes | removes the defensive deck selection, if allowed |
| POST | `/users/me/decks/validate` | yes | validates a composition without saving it |

Create/replace:

```json
{
  "name": "Solar control",
  "cards": [{ "cardId": 42, "quantity": 2 }, { "cardId": 51, "quantity": 3 }]
}
```

Deck response: `id`, `name`, `status` (`INCOMPLETE`, `VALID`, `INVALID`), `cards` with card and quantity, `totalCards`, `isDefenseDeck`, `updatedAt`, `validationErrors`.

Constraints from the project context: a valid deck has 30 cards, with copy limits per rarity/card and a maximum of 15 cards per faction; quantities cannot exceed the player's collection. Apply current limits after trades and Wanderer expiration as well. Confirm any numbers/rules that have not been fixed.

Proposed specific errors: `DECK_NOT_FOUND` 404, `DECK_INVALID` 422, `CARD_NOT_OWNED` 409, `DECK_NAME_ALREADY_USED` 409, `DEFENSE_DECK_INVALID` 409, `CARD_NOT_FOUND` 404. Validation responses should report errors per card/field rather than a single string.

## To decide

- Whether the catalog and card profiles include inactive cards for administrators (outside this player-facing contract).
- Sorting, filters, maximum number of decks, and name length.
- Whether saving allows incomplete/invalid decks (the schema has a status, so it appears to); only valid decks are used in a match.
- Whether deleting the defensive deck leaves the player without one or requires selecting another first.
