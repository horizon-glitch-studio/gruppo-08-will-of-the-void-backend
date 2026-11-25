# Will of the Void – Backend

Backend for **Will of the Void**, a digital collectible card game developed as part of the Laboratorio Integrato (Group 08).

Team: Mondir Badaoui, Gianluca Salzarulo, Simone Albanese\
Stack: Java 25, Spring Boot 4.1.2, PostgreSQL 17.

## Documentation

- [Tech stack](docs/tech-stack.md): technologies and dependencies, with the reasons behind each choice.
- [Database schema](docs/database/database-schema.md): 31 tables grouped into 8 areas, with Mermaid diagrams and relationships.
- [SQL schema](docs/database/schema.sql): PostgreSQL schema reference. The initial Flyway migration is [`V1__init.sql`](api/src/main/resources/db/migration/V1__init.sql), a copy of this file.
- [Game engine schema](docs/game-engine-schema.md): how the server simulates a match, from input to result, with Mermaid diagrams.

## Project structure

Maven multi-module project:

| Module | Content |
| --- | --- |
| [`engine`](engine) | Game engine in plain Java with JUnit tests. It has no dependency on Spring or the database, and a test fails the build if one is added. |
| [`api`](api) | Spring Boot REST API: entities, repositories, services, controllers, security and Flyway migrations. It depends on `engine`. |

Run `./mvnw test` from the repository root to build and test both modules (the `api` tests need PostgreSQL, see below).

The engine is a library, not a separate service: it receives the two decks and a seed and returns the result and the turn-by-turn log, so `api` calls it directly. This meets the brief's isolated, JUnit-tested component without a second process to deploy.

### Packages of the `api` module

The code under `com.horizonglitch.wotv` is organised by feature, not by layer: each package holds the entities, repositories, services, controllers and DTOs of one area, so a feature is read and changed in one place.

| Package | Area |
| --- | --- |
| `auth` | Registration, login, JWT refresh, email verification, password reset |
| `player` | Profile, avatar, time zone, settings |
| `shard` | Shard balance and bonuses |
| `notification` | Notifications |
| `card` | Catalog: factions, advantages, rarities, cards, starter deck |
| `collection` | Owned cards and departed Wanderers |
| `deck` | Decks, validation, defense deck |
| `pack` | Pack types, events, openings, guarantees |
| `trade` | Trades |
| `battle` | Battles, challenge requests, rematches, replays (calls `engine`) |
| `matchmaking` | Opponent lists, attack limits, shield |
| `social` | Friendships |
| `season` | Seasons, reset, Orbits, leaderboard |
| `backoffice` | Administration endpoints (ADMIN role) |
| `security`, `config`, `common` | Security and JWT, configuration, shared error handling and pagination |

Inside a package, classes are named after their role: `Deck` (entity), `DeckRepository`, `DeckService`, `DeckController`, `CreateDeckRequest` and `DeckResponse` (DTOs, Java records). A package may use another package's services, never its repositories.

## Getting Started

- Install Docker.
- Copy `.env.example` to `.env` and adjust the environment variables as needed.
- To run the API from IntelliJ, start `WillofthevoidApplication`: Spring Boot's Docker Compose support starts the database from `compose.yaml` and connects to it, and the API reads the variables of `.env` (such as `APP_JWT_SECRET`) by itself. JDK 25 is required.
- To run everything in Docker instead, run `docker compose --profile app up -d --build` to build and start the database and the backend.
- The backend is available at [http://localhost:8080](http://localhost:8080/) (Swagger UI: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)).
- Run `docker compose --profile app down` to stop the containers. Add `-v` to delete the database too, which is needed when `V1__init.sql` changes before the first release.

### Without Java installed

The `maven` service of `compose.yaml` holds JDK 25 and runs the Maven wrapper on the current sources, so only Docker is needed. Its tests use their own empty database (`postgres-test`, kept in memory), as in the CI, and never touch the data of `postgresdb`.

- `docker compose run --rm maven` builds both modules and runs all the tests.
- Any other Maven command works too, for example `docker compose run --rm maven ./mvnw -pl engine test` for the engine tests only, or `docker compose run --rm maven bash` for a shell with Java.
- The first run downloads the dependencies into the `maven-cache` volume; the next ones reuse it.
- Run `docker compose stop postgres-test` to stop the test database when you are done.

### Environment variables

`APP_FRONTEND_URL` is the base URL of the React app and must include its `/play` path. It defaults to `http://localhost:5173/play`; in production, set it to `https://<domain>/play` so verification and password reset links open the correct routes.
