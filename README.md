# Will of the Void – Backend

Backend of **Will of the Void**, a digital collectible card game developed for the Laboratorio Integrato (group 08).

Stack: Java 25, Spring Boot 4.1.2, PostgreSQL. The source code will be added in the next phases.

## Documentation

- [Tech stack](docs/tech-stack.md): technologies and dependencies, with the reasons behind each choice.
- [Database schema](docs/database/database-schema.md): the 28 tables in 8 views, with Mermaid diagrams and relationships.
- [SQL schema](docs/database/schema.sql): PostgreSQL schema reference. The initial Flyway migration is [`db/V1__init.sql`](db/V1__init.sql) at the repository root.
- [Game engine schema](docs/game-engine-schema.md): how the server simulates a match, from input to result, with Mermaid diagrams.
