# API Contract: Packs and Shards

Prefix: `/api/v1`. Based on `pack_type`, `pack_slot`, `pack_slot_rarity`, `event_pack_card`, `pack_opening`, `pack_opening_card`, `pack_guarantee`, and `shard_transaction`.

## Routes

| Method | Route | Auth | Result |
|---|---|---|---|
| GET | `/packs` | yes | packs currently available for purchase |
| GET | `/packs/{packTypeId}` | yes | details and cost |
| POST | `/packs/{packTypeId}/open` | yes | purchases and opens a pack in one atomic operation, 201 |
| GET | `/users/me/pack-openings` | yes | paginated opening history |
| GET | `/users/me/pack-openings/{openingId}` | yes | details of cards received |
| GET | `/users/me/shard-transactions` | yes | paginated transaction history |

Each pack exposes the odds of every slot and its guarantees.

Example opening:

```json
{
  "id": 901,
  "pack": { "id": 3, "name": "Event: Eclipse" },
  "shardsSpent": 300,
  "shardBalance": 420,
  "cards": [
    { "card": { "id": 42, "name": "Aster" }, "quantity": 1, "expiresAt": "2026-11-15T16:00:00Z", "isNew": true }
  ],
  "createdAt": "2026-11-08T16:00:00Z"
}
```

`POST .../open` does not accept cards or rarities from the client: the server checks availability (the standard pack is always on sale, an event pack only during its event, one event at a time), deducts Shards, and draws cards within a transaction. If a Wanderer still on board is drawn again, its stay is extended by 7 days; if it has already left, it comes back with a new full period.

Shard transaction types: `INVITE_BONUS`, `REGISTRATION_BONUS`, `DAILY_BONUS`, `COMEBACK_BONUS`, `SEASON_REWARD`, `ORBIT_REWARD`, `PACK_PURCHASE`. The balance must also be returned in the profile and updated atomically; the client never sends a balance to be saved.

There are no purchase limits. Opening and transaction histories are kept for the lifetime of the account.

Proposed specific errors: `PACK_NOT_FOUND` 404, `PACK_NOT_AVAILABLE` 409, `INSUFFICIENT_SHARDS` 409 (include `shardBalance` and `shardCost`), `PACK_OPENING_FAILED` 500. Repeating the same request could charge twice: support `Idempotency-Key` for purchases.