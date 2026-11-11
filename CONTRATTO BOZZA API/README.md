# Draft API Contract — Index

Draft derived from the functional context and the PostgreSQL schema in `docs/database/schema.sql`. It does not describe routes currently implemented. Confirmed decisions are stated directly; open items are listed in the final sections.

## Common conventions

- Base URL: `/api/v1`.
- JSON request and response bodies (`application/json`); `camelCase` names; UTC ISO 8601 dates.
- Routes acting on the authenticated player use `Authorization: Bearer <accessToken>`.
- Errors follow the common format and codes defined in [01-auth-profile.md](01-auth-profile.md#0-conventions); each area adds the specific codes described in its file.
- IDs are integers (`bigint` in the database); clients should treat them as strings if their runtime cannot represent large integers without loss.
- Paginated lists: `page` (0-based), `size` (default 20, maximum 100), response `{ "items": [], "page": 0, "size": 20, "totalItems": 0, "totalPages": 0 }`.

## Contracts by area

1. [Authentication and profile](01-auth-profile.md)
2. [Cards, collection, and decks](02-cards-collection-decks.md)
3. [Packs and credits](03-packs-credits.md)
4. [Trades](04-trades.md)
5. [Battles and matchmaking](05-battles-matchmaking.md)
6. [Friendships, notifications, and presence](06-social-notifications.md)