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
| [`api`](api) | Spring Boot REST API: entities, repositories, security, controllers and Flyway migrations. It depends on `engine`. |

Run `./mvnw test` from the repository root to build and test both modules (the `api` tests need PostgreSQL, see below).

## Getting Started

- Install Docker.
- Copy `.env.example` to `.env` and adjust the environment variables as needed.
- Run `docker compose up -d --build` to build and start the containers.
- The backend is available at [http://localhost:8080](http://localhost:8080/).
- Run `docker compose down` to stop the containers. Add `-v` to delete the database too, which is needed when `V1__init.sql` changes before the first release.
