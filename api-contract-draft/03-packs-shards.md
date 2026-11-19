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

Shard transaction types: `INVITE_BONUS`, `REGISTRATION_BONUS`, `DAILY_BONUS`, `COMEBACK_BONUS`, `EMAIL_BONUS`, `SEASON_REWARD`, `ORBIT_REWARD`, `PACK_PURCHASE`. The balance must also be returned in the profile and updated atomically; the client never sends a balance to be saved.

There are no purchase limits. Opening and transaction histories are kept for the lifetime of the account.

Proposed specific errors: `PACK_NOT_FOUND` 404, `PACK_NOT_AVAILABLE` 409, `INSUFFICIENT_SHARDS` 409 (include `shardBalance` and `shardCost`), `PACK_OPENING_FAILED` 500. Repeating the same request could charge twice: support `Idempotency-Key` for purchases.

## Implemented behaviour

- **`GET /packs`** lists the active pack types on sale now: standard packs always, an event pack only between `availableFrom` and `availableUntil`. Each pack has `slots` (position and rarity odds in percent), `guarantees` (`CHAMPION` every 10 packs, plus `ELECT` every 50 for standard packs or `WANDERER` every 50 for event packs) and, for an event, its Wanderer cards in `eventCards`. **`GET /packs/{id}`** also returns a pack not on sale, with `available: false`.
- **Opening response.** `id`, `pack` (`id`, `name`, `kind`), `shardsSpent`, `shardBalance` after the purchase, `guarantee` (the guarantee that replaced the last card, or `null` when luck was enough), `cards` (one item per card with `quantity`, `expiresAt` for Wanderers and `isNew`) and `createdAt`.
- **Errors.** `INSUFFICIENT_SHARDS` puts `shardBalance` and `shardCost` in the `details` object of the error. `PACK_OPENING_FAILED` means a rarity of the pack has no card to draw: nothing is charged. `GET /users/me/pack-openings/{id}` of another player, or that does not exist, is 404 `PACK_OPENING_NOT_FOUND`.
- **Concurrency.** Opening locks the player's guarantee counters, so two packs of the same player are drawn one after the other and cannot both use or both miss a guarantee.
- **Not implemented yet.** `Idempotency-Key`: the schema has no table to remember keys. A repeated request buys a second pack.
- **Shard transactions** are sorted newest first; each item has `id`, `amount` (negative for purchases), `reason`, `packOpeningId`, `invitedPlayerId`, `seasonId`, `orbit` and `createdAt`.
