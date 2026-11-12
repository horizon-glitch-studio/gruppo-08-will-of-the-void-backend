# API Contract: Battles and Matchmaking

Prefix: `/api/v1`. Based on `battle`, `battle_log`, `opponent_list`, and `opponent_list_entry`.

## QUICK/RANKED opponents

| Method | Route | Auth | Result |
|---|---|---|---|
| GET | `/matchmaking/opponents?mode=QUICK` | yes | current list (5 entries) |
| POST | `/matchmaking/opponents/refresh` | yes | regenerates the list if cooldown allows |
| POST | `/matchmaking/opponents/{opponentId}/battle` | yes | starts QUICK or RANKED against an opponent not yet attacked |

The start request body specifies `mode` and `deckId`. The list is persisted: refreshing the page does not change it; an opponent on the list can be attacked only once. `trophiesAtGeneration` is informational; resolution uses current trophy counts. RANKED updates trophies according to the Elo rule still to be defined; QUICK awards no trophies.

## FRIEND challenges

| Method | Route | Auth | Result |
|---|---|---|---|
| POST | `/battles/friend` | yes | sends a challenge to an online friend, 201 `PENDING` |
| GET | `/battles/{battleId}` | yes | status/details for either party |
| POST | `/battles/{battleId}/accept` | yes | accepts and selects a defensive deck |
| POST | `/battles/{battleId}/reject` | yes | rejects |
| POST | `/battles/{battleId}/cancel` | yes | challenger cancels before acceptance |

Proposed challenge body: `{ "opponentUsername": "star_hunter", "deckId": 12 }`. The challenge expires after 30 seconds according to the context; decide whether acceptance and match start happen over REST or WebSocket.

## Match, results, and history

| Method | Route | Auth | Result |
|---|---|---|---|
| POST | `/battles/{battleId}/actions` | yes | submits a game action; `BattleActionRequest` schema to be defined |
| GET | `/battles/{battleId}/result` | yes | final result and energy/trophy deltas |
| GET | `/users/me/battles` | yes | paginated history; proposed filters: `mode`, `outcome`, dates |
| GET | `/battles/{battleId}/replay` | yes | deck snapshots and turn log, if authorized |

The client does not send the seed, outcome, final energy, or trophy deltas: these are authoritative on the server. A match starts at zero energy; the win condition is reaching 30. For RANKED matches, the persisted result contains both players' deltas. Turn and action structure depends on the game engine and must be agreed before freezing the contract.

Proposed specific errors: `BATTLE_NOT_FOUND` 404, `BATTLE_FORBIDDEN` 403, `BATTLE_NOT_PENDING` 409, `BATTLE_EXPIRED` 409, `OPPONENT_NOT_IN_LIST` 409, `OPPONENT_ALREADY_ATTACKED` 409, `MATCHMAKING_COOLDOWN` 429, `DECK_INVALID` 422, `OPPONENT_NOT_ONLINE` 409, `INVALID_BATTLE_ACTION` 400.

## To decide

- Matchmaking trophy range and opponent selection/refresh/cooldown algorithm.
- Elo rules, draws, and temporary protection (`protectedUntil`).
- Action format, server authority, reconnection, and idempotency.
- Replay access and retention; log visibility during a match.
- Real-time transport: WebSocket/STOMP or an alternative, and related events.
