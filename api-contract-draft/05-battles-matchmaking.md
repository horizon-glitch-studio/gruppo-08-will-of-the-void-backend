# API Contract: Battles and Matchmaking

Prefix: `/api/v1`. Based on `battle`, `battle_log`, `opponent_list`, and `opponent_list_entry`.

## QUICK/RANKED opponents

| Method | Route | Auth | Result |
|---|---|---|---|
| GET | `/matchmaking/opponents?mode=QUICK` | yes | current list (5 entries) |
| POST | `/matchmaking/opponents/refresh` | yes | regenerates the list if cooldown allows; body `{ "mode": "QUICK" }` |
| POST | `/matchmaking/opponents/{opponentId}/battle` | yes | starts QUICK or RANKED against an opponent not yet attacked |

`mode` is `QUICK` or `RANKED`. The list is generated the first time it is requested. Each entry has `position`, `opponent` (`id`, `username`), `trophiesAtGeneration`, `orbit`, `attacked` and `battleId`; the list also returns `generatedAt` and `refreshAvailableAt`.

The start request body specifies `mode` and `deckId`: `{ "mode": "RANKED", "deckId": 12 }`. The response is the battle (201), `PLAYED` or `PENDING`. The list is persisted: refreshing the page does not change it; an opponent on the list can be attacked only once, and the entry is linked to the battle only when it is played, so a rejected or expired challenge leaves the opponent available. In QUICK, if the opponent has no valid defense deck, the battle becomes a challenge (`PENDING`, as for FRIEND); RANKED is always instant. Opponent search, refresh, attack limits and shield follow the rules. `trophiesAtGeneration` is informational; resolution uses current trophy counts. RANKED updates trophies with the Elo rule; QUICK awards no trophies. In RANKED the attacker needs a valid defense deck too. A shield starts after 5 RANKED attacks received in 24 hours, counted from the end of the previous shield, and ends when its owner attacks in RANKED.

## FRIEND challenges

| Method | Route | Auth | Result |
|---|---|---|---|
| POST | `/battles/friend` | yes | battle against a friend |
| GET | `/battles/{battleId}` | yes | status/details for either party |
| POST | `/battles/{battleId}/accept` | yes | accepts choosing one of the player's decks |
| POST | `/battles/{battleId}/reject` | yes | rejects |
| POST | `/battles/{battleId}/cancel` | yes | challenger cancels before acceptance |
| POST | `/battles/{battleId}/rematch` | yes | starts a rematch of a FRIEND or QUICK battle |

`POST /battles/friend` plays the battle at once if the friend has a valid defense deck (201 `PLAYED`); otherwise it creates a challenge (201 `PENDING`). The two players must be friends. The accept request body is `{ "deckId": 12 }`.

A battle has `id`, `mode`, `status`, `challenger` and `opponent` (`id`, `username`), `outcome` (`CHALLENGER_WIN`, `OPPONENT_WIN`, `DRAW`), `challengerEnergy`, `opponentEnergy`, `challengerTrophiesDelta`, `opponentTrophiesDelta`, `createdAt`, `expiresAt`, `playedAt` and `rematchOfId`.

Proposed challenge body: `{ "opponentUsername": "star_hunter", "deckId": 12 }`. The challenger's deck is saved as it is when the challenge is sent. A challenge expires 48 hours after it is sent and the challenger can cancel it before then; between two players there can be at most one pending challenge.

The rematch request body is `{ "deckId": 12 }`. Either participant in a completed FRIEND or QUICK battle can request a rematch, with no limit on the number of rematches. The new battle uses the same mode and opponent and is linked to the original through `rematchOfId`. It starts immediately when the opponent has a valid defense deck; otherwise it creates a `PENDING` challenge that the opponent must accept. RANKED battles cannot be rematched. The selected deck must be valid and owned by the requesting player.

## Match, results, and history

| Method | Route | Auth | Result |
|---|---|---|---|
| GET | `/battles/{battleId}/result` | yes | final result and energy/trophy deltas |
| GET | `/users/me/battles` | yes | paginated history, newest first; filters: `mode`, `status`, `result` (`WIN`, `LOSS`, `DRAW` from the player's side), `from`, `to` (creation time) |
| GET | `/battles/{battleId}/replay` | yes | deck snapshots and turn log, if authorized |

The client does not send the seed, outcome, final energy, or trophy deltas: these are authoritative on the server, which simulates the whole match at once. A match starts at zero energy; the win threshold depends on the mode: 30 in RANKED and FRIEND, 20 in QUICK. For RANKED matches, the persisted result contains both players' deltas. Both participants can watch the replay.

The result has `battleId`, `mode`, `outcome`, `endReason` (`ENERGY_THRESHOLD` or `DECK_EXHAUSTED`), `winThreshold`, `playedAt`, and `challenger` and `opponent` with `player`, `energy` and `trophiesDelta`.

Replays are available only to the two participants after the match ends and are kept for 90 days; the result stays in the history. The replay is a Battle Viewer document (`docs/battle-viewer.schema.json`).

Specific errors: `BATTLE_NOT_FOUND` 404, `BATTLE_FORBIDDEN` 403, `BATTLE_NOT_PENDING` 409 (with `details.status`), `BATTLE_EXPIRED` 409, `BATTLE_NOT_PLAYED` 409, `BATTLE_ALREADY_PENDING` 409, `NOT_FRIENDS` 409, `REMATCH_NOT_ALLOWED` 409, `REPLAY_EXPIRED` 410, `OPPONENT_NOT_IN_LIST` 409, `OPPONENT_ALREADY_ATTACKED` 409, `ATTACK_LIMIT_REACHED` 409 (with `details.limit`), `OPPONENT_SHIELDED` 409, `OPPONENT_NOT_AVAILABLE` 409, `DEFENSE_DECK_REQUIRED` 409, `MATCHMAKING_COOLDOWN` 429 (with `details.refreshAvailableAt`), `DECK_INVALID` 422, `DECK_NOT_FOUND` 404, `PLAYER_NOT_FOUND` 404.

## Leaderboard and seasons

| Method | Route | Auth | Result |
|---|---|---|---|
| GET | `/leaderboard` | yes | paginated; optional `orbit` filter; only players with at least one RANKED battle in the current season |
| GET | `/seasons/current` | yes | current season with start and end dates |

Leaderboard items contain `position`, `id`, `username`, `avatar`, `trophies` and `orbit`. Equal Trophy totals share `position`; the optional Orbit filter returns positions within that Orbit. The Orbit codes are `ORBIT_1` to `ORBIT_4`, with names Brink Orbit (`Orbita del Bordo`), Drift Orbit (`Orbita della Deriva`), Halo Orbit (`Orbita dell'Alone`) and Zenith Orbit (`Orbita dello Zenit`). Pagination follows the common `page`/`size` convention.
