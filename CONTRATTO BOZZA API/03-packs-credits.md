# API Contract: Packs and Credits

Prefix: `/api/v1`. Based on `pack_type`, `pack_slot`, `pack_opening`, `pack_opening_card`, and `credit_transaction`.

## Routes

| Method | Route | Auth | Result |
|---|---|---|---|
| GET | `/packs` | yes | packs currently available for purchase |
| GET | `/packs/{packTypeId}` | yes | details and cost |
| POST | `/packs/{packTypeId}/open` | yes | purchases and opens a pack in one atomic operation, 201 |
| GET | `/users/me/pack-openings` | yes | paginated opening history |
| GET | `/users/me/pack-openings/{openingId}` | yes | details of cards received |
| GET | `/users/me/credit-transactions` | yes | paginated transaction history |

Each pack exposes the odds of every slot and its guarantees.

Example opening:

```json
{
  "id": 901,
  "pack": { "id": 3, "name": "Event: Eclipse" },
  "creditsSpent": 300,
  "creditBalance": 420,
  "cards": [
    { "card": { "id": 42, "name": "Aster" }, "quantity": 1, "expiresAt": "2026-11-15T16:00:00Z", "isNew": true }
  ],
  "createdAt": "2026-11-08T16:00:00Z"
}
```

`POST .../open` does not accept cards or rarities from the client: the server checks availability (the standard pack is always on sale, an event pack only during its event, one event at a time), deducts credits, and draws cards within a transaction. If a Wanderer still on board is drawn again, its stay is extended by 7 days; if it has already left, it comes back with a new full period.

Credit transaction types: `INVITE_BONUS`, `REGISTRATION_BONUS`, `DAILY_BONUS`, `COMEBACK_BONUS`, `SEASON_REWARD`, `PACK_PURCHASE`. The balance must also be returned in the profile and updated atomically; the client never sends a balance to be saved.

Proposed specific errors: `PACK_NOT_FOUND` 404, `PACK_NOT_AVAILABLE` 409, `INSUFFICIENT_CREDITS` 409 (include `creditBalance` and `creditCost`), `PACK_OPENING_FAILED` 500. Repeating the same request could charge twice: support `Idempotency-Key` for purchases.

## To decide

- Purchase limits.
- History format and retention.
- Daily bonus: the auth contract grants it at login; decide whether to add a dedicated endpoint for manual claiming.
