# API Contract: Trades

Prefix: `/api/v1`. Based on `trade` and `trade_card`. A trade has a proposer and a recipient, and a status of `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`, `EXPIRED`, or `FAILED`; expected duration is 48 hours.

## Routes

| Method | Route | Auth | Result |
|---|---|---|---|
| GET | `/trades` | yes | user's trades; proposed filters: `status`, `direction` (`incoming`/`outgoing`) |
| POST | `/trades` | yes | proposes a trade, 201 |
| GET | `/trades/{tradeId}` | yes | details if the user is one of the parties |
| POST | `/trades/{tradeId}/accept` | yes | accepts as recipient, 200 |
| POST | `/trades/{tradeId}/reject` | yes | rejects as recipient, 200 |
| POST | `/trades/{tradeId}/cancel` | yes | cancels as proposer, 200 |

Create:

```json
{
  "recipientUsername": "star_hunter",
  "offeredCards": [{ "cardId": 17, "quantity": 1 }],
  "requestedCards": [{ "cardId": 51, "quantity": 2 }]
}
```

Proposed response: trade with `id`, `status`, `proposer`, `recipient`, `offeredCards`, `requestedCards`, `createdAt`, `expiresAt`, and `closedAt`. Acceptance transfers cards atomically and recalculates both players' deck status. The server rechecks ownership and quantity at acceptance with a lock; non-tradable cards (Wanderers) are not allowed. The recipient cannot accept after expiration.

Proposed specific errors:

- `TRADE_NOT_FOUND` 404: trade does not exist.
- `PLAYER_NOT_FOUND` 404: recipient username does not exist (same code as the public profile endpoint).
- `TRADE_FORBIDDEN` 403: caller is not the proposer or recipient, or is a party but lacks permission for that action (for example, the proposer tries to accept). This check also applies when a caller directly requests a trade by ID.
- `EMAIL_NOT_VERIFIED` 403: proposer must verify their email before trading.
- `TRADE_INVALID_CARDS` 400: invalid card IDs or quantities, non-tradeable cards, an empty trade, duplicate `cardId` entries within the same list, or attempting to trade with oneself. To trade multiple copies of a card, use one entry with `quantity` greater than 1, as in the example above.
- `TRADE_ALREADY_PENDING` 409: a pending trade already exists between the same pair of players.
- `TRADE_NOT_PENDING` 409: trade is no longer pending, including when it has expired; the response includes its current status.
- `TRADE_MIN_COLLECTION` 409: accepting would leave a player with fewer than 30 cards; the trade remains pending.
- `TRADE_FAILED` 409: at acceptance, one or more cards are no longer available. The server closes the trade as `FAILED` without transferring any cards and returns this error; the client can retrieve the updated trade with `GET /trades/{tradeId}`.

A trade can hold any number of cards on each side, and one side can be empty (a gift); every trade moves at least one card. Self-trading is forbidden.

The other party is notified when a trade is received and when it reaches a final status (accepted, rejected, cancelled, expired, failed).

## Implemented behaviour

- **`TRADE_INVALID_CARDS`** lists every problem in `fieldErrors`: `TRADE_WITH_SELF`, `TRADE_EMPTY`, `CARD_DUPLICATED`, `CARD_ON_BOTH_SIDES` (the same card offered and requested), `QUANTITY_INVALID`, `CARD_NOT_FOUND`, `CARD_NOT_TRADEABLE`.
- **Ownership** is not checked at proposal (TRADE-10): the same copy can be offered to several players.
- **Acceptance** checks, in order: the trade is pending and not expired (`TRADE_NOT_PENDING`), the recipient's email is verified (`EMAIL_NOT_VERIFIED`), both players still own every card (`TRADE_FAILED`, and the trade is saved as `FAILED`), nobody is left with fewer than 30 permanent cards (`TRADE_MIN_COLLECTION`, the trade stays pending). Wanderer cards and archived cards do not count towards the 30.
- **`TRADE_NOT_PENDING`** puts the current status in `details.status`. A trade past its deadline is expired as soon as someone acts on it, even before the expiry job runs; a job also expires old proposals every minute.
- **A pending trade past its deadline** does not block a new proposal between the same players: it is expired first.
- **`GET /trades`** is paginated, newest first; `direction` other than `incoming` or `outgoing` is 400 `VALIDATION_FAILED` with `DIRECTION_INVALID`.
- **Notifications**: `TRADE_RECEIVED` to the recipient; `TRADE_ACCEPTED`, `TRADE_REJECTED`, `TRADE_FAILED` to the proposer; `TRADE_CANCELLED` to the recipient; `TRADE_EXPIRED` to both. A player whose deck becomes invalid after the trade gets `DECK_INVALID`.
- **Deleting an account** cancels its pending trades and notifies the other players.
