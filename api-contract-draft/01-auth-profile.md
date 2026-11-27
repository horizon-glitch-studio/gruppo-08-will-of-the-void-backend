# API Contract: Authentication and Profile

All routes use the `/api/v1` prefix. Data format: `application/json`, camelCase fields, dates in ISO 8601 UTC.
Player-facing text is English only: the backend returns stable `code` values and English messages for developers; the frontend translates text displayed in the UI.

Confirmed decisions are stated as contract rules. Only the items listed at the end remain open.

## 0. Conventions

**Authentication.** Protected routes require `Authorization: Bearer <accessToken>`.
**Roles.** The DB role is `USER` or `ADMIN`. Spring Security adds the `ROLE_` prefix internally; the API uses `USER` / `ADMIN`.
**Uniqueness.** Usernames and email addresses are unique case-insensitively (`uq_player_username_lower`, `uq_player_email_lower`).
**Deleted accounts.** Deletion is a soft delete (`deleted_at`): username and email are anonymized. A deleted account cannot log in, and its public profile returns 404.
**Pagination.** Paginated lists use `page` (starting at 0) and `size` (default 20, maximum 100). Lists are sorted by ID; histories and notifications are sorted newest first.

### Error format

```json
{
  "timestamp": "2026-11-08T16:22:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "EMAIL_ALREADY_USED",
  "message": "This email is already registered.",
  "path": "/api/v1/auth/register",
  "fieldErrors": []
}
```

| Field | Type | Description |
| :--- | :--- | :--- |
| `timestamp` | String (ISO 8601) | Error date and time |
| `status` | Integer | HTTP status code |
| `error` | String | Short HTTP error name |
| `code` | String | Stable uppercase code; the frontend uses it to choose the text to display |
| `message` | String | English description for developers, not for display to players |
| `path` | String | Requested route |
| `fieldErrors` | Array | Only for validation 400 responses. Each item is `{ "field": "...", "code": "..." }`; otherwise `[]` |
| `details` | Object | Only for errors that carry extra values, such as `shardBalance` and `shardCost` for `INSUFFICIENT_SHARDS`; missing otherwise |

Example validation 400:

```json
{
  "timestamp": "2026-11-08T16:22:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "One or more fields are invalid.",
  "path": "/api/v1/auth/register",
  "fieldErrors": [
    { "field": "username", "code": "USERNAME_INVALID_CHARACTERS" },
    { "field": "password", "code": "PASSWORD_TOO_SHORT" }
  ]
}
```

### Error codes

| Code | HTTP | When |
| :--- | :--- | :--- |
| `VALIDATION_FAILED` | 400 | One or more fields are invalid (details in `fieldErrors`) |
| `INVALID_CREDENTIALS` | 401 | Incorrect username/email or password (same response in either case) |
| `TOKEN_INVALID` | 401 | JWT or refresh token is missing, malformed, or expired |
| `FORBIDDEN` | 403 | Insufficient role |
| `PLAYER_NOT_FOUND` | 404 | Profile does not exist or account was deleted |
| `USERNAME_ALREADY_USED` | 409 | Username is already in use |
| `EMAIL_ALREADY_USED` | 409 | Email is already in use |
| `TIMEZONE_CHANGE_TOO_SOON` | 409 | The time zone was changed less than 30 days ago |
| `INVITE_CODE_INVALID` | 400 | Invite code does not exist |
| `ACTION_TOKEN_INVALID` | 400 | Email verification or password reset token is incorrect, expired, or already used |
| `TOO_MANY_REQUESTS` | 429 | Code reserved for rate limiting, deferred to phase 2; not part of v1 outcomes |
| `INTERNAL_ERROR` | 500 | Server error |

`fieldErrors[].code` values: `FIELD_REQUIRED` (a required field is missing or blank), `USERNAME_TOO_SHORT`, `USERNAME_TOO_LONG`, `USERNAME_INVALID_CHARACTERS`, `USERNAME_RESERVED`, `PASSWORD_TOO_SHORT`, `PASSWORD_TOO_LONG`, `EMAIL_INVALID`, `TIMEZONE_INVALID`, `AVATAR_INVALID`.

## 1. Authentication

### 1.1 Registration

Creates an account. Upon registration, the player receives the registration Shard bonus (transaction `REGISTRATION_BONUS`, once per player), and the backend sends a verification email.

- **URL:** `/auth/register`
- **Method:** `POST`
- **Authentication:** none

Request:

```json
{
  "username": "void_walker",
  "email": "player@willofthevoid.com",
  "password": "Password123!",
  "timezone": "Europe/Rome",
  "inviteCode": "VOID-4F7K"
}
```

| Field | Required | Notes |
| :--- | :--- | :--- |
| `username` | yes | 3 to 20 characters, letters, numbers, and `_` only. Reserved words (`me`, `admin`, ...) are not allowed. |
| `email` | yes | Valid email format, up to 255 characters |
| `password` | yes | 8 to 72 characters. |
| `timezone` | yes | IANA time zone read from the browser (e.g. `Europe/Rome`), validated by the backend. Used for the daily bonus |
| `inviteCode` | no | Invite code belonging to another player |

**201 Created** response:

```json
{
  "id": 1,
  "username": "void_walker",
  "email": "player@willofthevoid.com",
  "emailVerified": false
}
```

Errors: 400 `VALIDATION_FAILED`, 400 `INVITE_CODE_INVALID`, 409 `USERNAME_ALREADY_USED`, 409 `EMAIL_ALREADY_USED`.

### 1.2 Login

Accepts either a username or email in the same field. If due, login grants the daily and comeback bonuses and returns their details. Logged-in players claim due bonuses with `POST /auth/daily-bonus`. Refreshing a token alone does not grant bonuses.

- **URL:** `/auth/login`
- **Method:** `POST`
- **Authentication:** none

Request:

```json
{
  "identifier": "void_walker",
  "password": "Password123!"
}
```

**200 OK** response:

```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "accessTokenExpiresIn": 900,
  "refreshToken": "r8Gk2...",
  "refreshTokenExpiresIn": 2592000,
  "player": {
    "id": 1,
    "username": "void_walker",
    "role": "USER",
    "emailVerified": false
  },
  "dailyBonus": {
    "granted": true,
    "amount": 10
  },
  "comebackBonus": {
    "granted": false,
    "amount": 0
  }
}
```

`expiresIn` is in seconds. If the bonus was already claimed today: `"dailyBonus": { "granted": false, "amount": 0 }`. `comebackBonus` is granted on top of the daily bonus after at least 7 days without an authenticated request, at most once every 30 days; otherwise `{ "granted": false, "amount": 0 }`. Numeric values in the example are illustrative.

Errors: 400 `VALIDATION_FAILED`, 401 `INVALID_CREDENTIALS`. Attempt limits and the corresponding 429 response are deferred to phase 2.

### 1.3 Token refresh

Exchanges a valid refresh token for a new token pair. The used refresh token is invalidated (`used_at` in `player_token`) and a new one is issued (rotation).

- **URL:** `/auth/refresh`
- **Method:** `POST`
- **Authentication:** none

Request:

```json
{ "refreshToken": "r8Gk2..." }
```

**200 OK** response: same `tokenType`, `accessToken`, `accessTokenExpiresIn`, `refreshToken`, and `refreshTokenExpiresIn` format as login (without `player` and `dailyBonus`).

Errors: 401 `TOKEN_INVALID`.

### 1.4 Daily reward claim

Claims any daily and comeback bonuses due for the authenticated player. Repeated claims do not grant a bonus twice. Comeback eligibility is retained when activity tracking detects the return before this claim.

- **URL:** `/auth/daily-bonus`
- **Method:** `POST`
- **Authentication:** required
- **Request:** no body

**200 OK** response:

```json
{
  "dailyBonus": { "granted": true, "amount": 100 },
  "comebackBonus": { "granted": false, "amount": 0 }
}
```

Uses the same response format as login. Each reason is recorded as a separate Shard transaction. Errors: 401 `TOKEN_INVALID`.

### 1.5 Logout

Invalidates the refresh token. The access token is stateless and remains valid until it expires.

- **URL:** `/auth/logout`
- **Method:** `POST`
- **Authentication:** required

Request:

```json
{ "refreshToken": "r8Gk2..." }
```

**204 No Content** response.

### 1.6 Email verification

- **URL:** `/auth/verify-email`
- **Method:** `POST`
- **Authentication:** none

Request:

```json
{ "token": "b7d1..." }
```

**204 No Content** response. Errors: 400 `ACTION_TOKEN_INVALID`.

### 1.7 Resend verification email

- **URL:** `/auth/resend-verification`
- **Method:** `POST`
- **Authentication:** required

No body. **204 No Content** response. Returns 204 even if the email is already verified.

### 1.8 Forgot password

Always returns the same response, whether or not the email exists, to avoid revealing which email addresses are registered.

- **URL:** `/auth/forgot-password`
- **Method:** `POST`
- **Authentication:** none

Request:

```json
{ "email": "player@willofthevoid.com" }
```

**202 Accepted** response (no body). Errors: 400 `VALIDATION_FAILED`. Attempt limits and the corresponding 429 response are deferred to phase 2.

### 1.9 Reset password

- **URL:** `/auth/reset-password`
- **Method:** `POST`
- **Authentication:** none

Request:

```json
{
  "token": "c91a...",
  "newPassword": "NewPassword456!"
}
```

**204 No Content** response. All of the player's refresh tokens are invalidated. Errors: 400 `ACTION_TOKEN_INVALID`, 400 `VALIDATION_FAILED`.

## 2. Player profile

### 2.1 Current profile (private data)

- **URL:** `/users/me`
- **Method:** `GET`
- **Authentication:** required

**200 OK** response:

```json
{
  "id": 1,
  "username": "void_walker",
  "email": "player@willofthevoid.com",
  "emailVerified": false,
  "role": "USER",
  "avatar": "avatar_07",
  "shardBalance": 120,
  "trophies": 340,
  "league": "ORBIT_2",
  "defenseDeckId": 12,
  "protectedUntil": null,
  "timezone": "Europe/Rome",
  "inviteCode": "VOID-4F7K",
  "invitedBy": null,
  "stats": {
    "matchesPlayed": 120,
    "wins": 70,
    "losses": 42,
    "draws": 8,
    "winRate": 58.3
  },
  "createdAt": "2026-11-08T16:00:00Z"
}
```

Field notes:

- `avatar`: key for a preset avatar (image uploads are not supported), stored as `player.avatar_id`.
- `league`: calculated from trophies, not stored. Stable code and player-facing name:
  - `ORBIT_1` — Brink Orbit / Orbita del Bordo — 0–199 trophies.
  - `ORBIT_2` — Drift Orbit / Orbita della Deriva — 200–499 trophies.
  - `ORBIT_3` — Halo Orbit / Orbita dell'Alone — 500–999 trophies.
  - `ORBIT_4` — Zenith Orbit / Orbita dello Zenit — 1000+ trophies.
- `defenseDeckId`: `null` if the player has no defensive deck. The frontend must also check whether the deck is valid (see the deck contract).
- `protectedUntil`: `null` if the player is not protected.
- `stats`: derived from the `battle` table (only matches with status `PLAYED`). `winRate` = `wins / matchesPlayed * 100`, rounded to one decimal place, or `0` if the player has not played. Draws count as matches played, not wins.
- `invitedBy`: username of the player who invited this player, or `null`.

Errors: 401 `TOKEN_INVALID`.

### 2.2 Update profile

- **URL:** `/users/me`
- **Method:** `PATCH`
- **Authentication:** required

Request (all fields are optional; send only the fields to change):

```json
{
  "avatar": "avatar_12",
  "timezone": "Europe/London"
}
```

**200 OK** response: same object as `GET /users/me`.
Errors: 400 `VALIDATION_FAILED` (`AVATAR_INVALID` for an unknown or inactive avatar, `TIMEZONE_INVALID`), 401 `TOKEN_INVALID`, 409 `TIMEZONE_CHANGE_TOO_SOON` (time zone changed less than 30 days ago). Sending the current time zone again changes nothing and does not count as a change.
Username and email **cannot** be changed in v1.

### 2.3 Accessibility settings

Correspond to the `player_settings` table.

- **URL:** `/users/me/settings`
- **Methods:** `GET` and `PATCH`
- **Authentication:** required

**200 OK** response (GET) and PATCH request/response (all PATCH fields are optional):

```json
{
  "reduceMotion": false,
  "reduceTransparency": false,
  "showTextLabels": false
}
```

Errors: 400 `VALIDATION_FAILED`, 401 `TOKEN_INVALID`.

### 2.4 Change password

- **URL:** `/users/me/password`
- **Method:** `PUT`
- **Authentication:** required

Request:

```json
{
  "currentPassword": "Password123!",
  "newPassword": "NewPassword456!"
}
```

**204 No Content** response. All of the player's refresh tokens are invalidated, so every device has to log in again; the access token in use stays valid until it expires.
Errors: 400 `VALIDATION_FAILED`, 401 `INVALID_CREDENTIALS` (incorrect current password).

### 2.5 Delete account

Soft delete: set `deleted_at`, anonymize username and email, and invalidate all tokens.

- **URL:** `/users/me`
- **Method:** `DELETE`
- **Authentication:** required

Request:

```json
{ "password": "Password123!" }
```

**204 No Content** response. Errors: 401 `INVALID_CREDENTIALS`.

### 2.6 Public profile

- **URL:** `/users/{username}`
- **Method:** `GET`
- **Authentication:** none (if access should be restricted to logged-in players, change to required)

**200 OK** response:

```json
{
  "id": 2,
  "username": "star_hunter",
  "avatar": "avatar_03",
  "trophies": 520,
  "league": "ORBIT_3",
  "stats": {
    "matchesPlayed": 80,
    "wins": 50,
    "losses": 28,
    "draws": 2,
    "winRate": 62.5
  },
  "createdAt": "2026-10-20T10:00:00Z"
}
```

Never expose: email, Shards, role, decks, time zone, or invite code.
Errors: 404 `PLAYER_NOT_FOUND`.

## 3. Open items

- **Leagues:** Orbit names remain to be defined; thresholds are set by the rules and the league is calculated from trophies.
- **Rate limiting:** Thresholds and implementation are deferred to phase 2. Do not promise `429 TOO_MANY_REQUESTS` on authentication routes in v1.
