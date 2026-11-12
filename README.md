# Will of the Void – Backend

Backend for **Will of the Void**, a digital collectible card game developed as part of the Laboratorio Integrato (Group 08).

Team: Mondir Badaoui, Gianluca Salzarulo, Simone Albanese\
Stack: Java 25, Spring Boot 4.1.2, PostgreSQL.

## Documentation

- [Tech stack](docs/tech-stack.md): technologies and dependencies, with the reasons behind each choice.
- [Database schema](docs/database/database-schema.md): 28 tables grouped into 8 areas, with Mermaid diagrams and relationships.
- [SQL schema](docs/database/schema.sql): PostgreSQL schema reference. The initial Flyway migration is [`db/V1__init.sql`](db/V1__init.sql) at the repository root.
- [Game engine schema](docs/game-engine-schema.md): how the server simulates a match, from input to result, with Mermaid diagrams.

## Getting Started

- Install Docker.
- Copy `.env.example` to `.env` and adjust the environment variables as needed.
- Run `docker compose up -d --build` to build and start the containers.
- The backend is available at [http://localhost:8080](http://localhost:8080/).
- Run `docker compose down` to stop the containers.
