# API Contract: Battles and Matchmaking

Prefix: `/api/v1`. Based on `battle`, `battle_log`, `opponent_list`, and `opponent_list_entry`.

## QUICK/RANKED opponents

| Method | Route | Auth | Result |
|---|---|---|---|
| GET | `/matchmaking/opponents?mode=QUICK` | yes | current list (5 entries) |
| POST | `/matchmaking/opponents/refresh` | yes | regenerates the list if cooldown allows |
| POST | `/matchmaking/opponents/{opponentId}/battle` | yes | starts QUICK or RANKED against an opponent not yet attacked |

The start request body specifies `mode` and `deckId`. The list is persisted: refreshing the page does not change it; an opponent on the list can be attacked only once. In QUICK, if the opponent has no valid defense deck, the battle becomes a challenge (`PENDING`, as for FRIEND); RANKED is always instant. Opponent search, refresh, attack limits and shield follow the rules. `trophiesAtGeneration` is informational; resolution uses current trophy counts. RANKED updates trophies with the Elo rule; QUICK awards no trophies.

## FRIEND challenges

| Method | Route | Auth | Result |
|---|---|---|---|
| POST | `/battles/friend` | yes | battle against a friend |
| GET | `/battles/{battleId}` | yes | status/details for either party |
| POST | `/battles/{battleId}/accept` | yes | accepts choosing one of the player's decks |
| POST | `/battles/{battleId}/reject` | yes | rejects |
| POST | `/battles/{battleId}/cancel` | yes | challenger cancels before acceptance |
| POST | `/battles/{battleId}/rematch` | yes | starts a rematch of a FRIEND or QUICK battle |

`POST /battles/friend` plays the battle at once if the friend has a valid defense deck (201 `PLAYED`); otherwise it creates a challenge (201 `PENDING`).

Proposed challenge body: `{ "opponentUsername": "star_hunter", "deckId": 12 }`. The challenger's deck is saved as it is when the challenge is sent. A challenge expires 48 hours after it is sent and the challenger can cancel it before then; between two players there can be at most one pending challenge.

The rematch request body is `{ "deckId": 12 }`. Either participant in a completed FRIEND or QUICK battle can request a rematch, with no limit on the number of rematches. The new battle uses the same mode and opponent and is linked to the original through `rematchOfId`. It starts immediately when the opponent has a valid defense deck; otherwise it creates a `PENDING` challenge that the opponent must accept. RANKED battles cannot be rematched. The selected deck must be valid and owned by the requesting player.

## Match, results, and history

| Method | Route | Auth | Result |
|---|---|---|---|
| GET | `/battles/{battleId}/result` | yes | final result and energy/trophy deltas |
| GET | `/users/me/battles` | yes | paginated history; proposed filters: `mode`, `outcome`, dates |
| GET | `/battles/{battleId}/replay` | yes | deck snapshots and turn log, if authorized |

The client does not send the seed, outcome, final energy, or trophy deltas: these are authoritative on the server, which simulates the whole match at once. A match starts at zero energy; the win threshold depends on the mode: 30 in RANKED and FRIEND, 20 in QUICK. For RANKED matches, the persisted result contains both players' deltas. Both participants can watch the replay.

Replays are available only to the two participants after the match ends and are kept for 90 days; the result stays in the history.

Proposed specific errors: `BATTLE_NOT_FOUND` 404, `BATTLE_FORBIDDEN` 403, `BATTLE_NOT_PENDING` 409, `BATTLE_EXPIRED` 409, `OPPONENT_NOT_IN_LIST` 409, `OPPONENT_ALREADY_ATTACKED` 409, `MATCHMAKING_COOLDOWN` 429, `DECK_INVALID` 422.

## Leaderboard and seasons

| Method | Route | Auth | Result |
|---|---|---|---|
| GET | `/leaderboard` | yes | paginated; optional `orbit` filter; only players with at least one RANKED battle in the current season |
| GET | `/seasons/current` | yes | current season with start and end dates |