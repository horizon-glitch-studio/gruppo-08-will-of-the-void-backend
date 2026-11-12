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

The player-facing list does not expose internal slot configurations or detailed odds unless the team decides to make them public. For transparency, include an explicit `odds` value in the pack, calculated from the slot rules.

Example opening:

```json
{
  "id": 901,
  "pack": { "id": 3, "name": "Event: Eclipse" },
  "creditsSpent": 100,
  "creditBalance": 420,
  "cards": [
    { "card": { "id": 42, "name": "Aster" }, "quantity": 1, "expiresAt": null, "isNew": true }
  ],
  "createdAt": "2026-11-08T16:00:00Z"
}
```

`POST .../open` does not accept cards or rarities from the client: the server checks availability dates, deducts credits, and draws cards within a transaction. If a previously owned Wanderer is drawn again, the server applies the game's rule for extending its stay.

Credit transaction types: `REGISTRATION_BONUS`, `DAILY_BONUS`, `INVITE_BONUS`, `PACK_PURCHASE`. The balance must also be returned in the profile and updated atomically; the client never sends a balance to be saved.

Proposed specific errors: `PACK_NOT_FOUND` 404, `PACK_NOT_AVAILABLE` 409, `INSUFFICIENT_CREDITS` 409 (include `creditBalance` and `creditCost`), `PACK_OPENING_FAILED` 500. Repeating the same request could charge twice: support `Idempotency-Key` for purchases.

## To decide

- Whether odds are public and how rarity upgrades are represented.
- Number and availability of packs, purchase limits, and any event windows.
- History format and retention.
- Daily bonus: the auth contract grants it at login; decide whether to add a dedicated endpoint for manual claiming.
