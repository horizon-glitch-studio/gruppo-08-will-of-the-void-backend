# Will of the Void – Backend tech stack

The backend is a Java 25 application built with Spring Boot 4.1.2 and PostgreSQL. Most of the stack is required by the project brief; the rest are libraries chosen for security, testing and API documentation.

## Language and core tools

Java, Spring Boot and GitHub are required by the brief. The other choices are the standards of the Spring ecosystem, so all three team members can find documentation and examples without integrating unusual tools.

| Technology | Role | Why |
| --- | --- | --- |
| Java 25 | Server-side language | Required by the brief. |
| Spring Boot 4.1.2 | Framework for the REST API and the business logic | Required by the brief. It configures the web server, database access and security automatically, so the team's time goes into the game rules rather than the infrastructure. |
| Maven | Build and dependency management | The default tool of Spring Initializr. It lets the game engine live in a separate module, as the brief requires ("an isolated Java component"). |
| Spring Data JPA + Hibernate 6 | Data access | Repositories generate the most common queries without hand-written SQL. Hibernate 6 maps PostgreSQL `jsonb` columns directly. |
| Bean Validation (Jakarta Validation) | Input validation | Constraints are written as annotations on the DTOs (`@NotNull`, `@Size`, `@Min`), and Spring rejects invalid requests before they reach the business logic. |
| Swagger (Springdoc OpenAPI) | API documentation | Required by the brief. The documentation is generated from the code, so it stays in sync with the API as it changes, and the frontend team can try the endpoints from the browser. |
| JUnit 5 | Automated tests | Required by the brief for the game engine. It is built into Spring Boot and supports parameterised tests, useful to check all 12 faction relationships with a single test. |
| Docker | Local environment | All three developers run the same PostgreSQL version with one command, which removes "it works on my machine" problems. |
| Git, GitHub, GitHub Projects | Version control and task tracking | Required by the brief. GitHub Projects links tasks to pull requests, so each member's progress is visible. |

## Project dependencies

Each dependency covers a requirement of the brief or a feature the team decided on. Lombok is the only one added for convenience, justified by the time it saves.

| Dependency | Role | Why |
| --- | --- | --- |
| Spring Web | REST controllers, HTTP routes, JSON exchange | The portal talks to the frontend only through a REST API, as the brief requires. Spring Web handles routing, JSON conversion and response codes. |
| Spring Security + JWT | Stateless authentication, access control, ROLE_USER and ROLE_ADMIN roles | The JWT travels with every request, so the server keeps no sessions: this suits a separate frontend and also authenticates the WebSocket connection. The roles separate players from the backoffice administrators the brief requires. In the database the role is stored as USER or ADMIN; Spring Security adds the `ROLE_` prefix. |
| Spring Data JPA / Hibernate | Mapping between Java objects and tables | The 22 tables become Java entities. Transactions and pessimistic locks protect trades, where the brief requires that no card is lost or duplicated. |
| Spring Validation (Hibernate Validator) | Request validation and server-side constraints | The brief requires the portal to check deck constraints. Server-side validation is the only reliable kind, because client-side validation can be bypassed. |
| JUnit 5 & AssertJ | Testing the game engine | The brief requires the engine to be isolated and covered by JUnit tests. AssertJ makes assertions read like sentences, which helps when checking long battle logs and edge cases. |
| Lombok | Less boilerplate | Generates getters, setters, constructors and builders, so entities stay short and readable. The whole team must use it the same way, decided at the start. |
| Springdoc OpenAPI (Swagger UI) | Interactive API documentation | The library that produces the Swagger documentation required by the brief, compatible with Spring Boot 4.1.2. |

The features the team decided on need two dependencies that are not in this list yet: Spring WebSocket (real-time FRIEND challenges and online friends) and Flyway (versioned schema migrations and initial data).

## Database and persistence

The brief names MySQL; the team uses PostgreSQL because three game rules can be enforced directly by the database instead of only in the code.

| Technology | Role | Why |
| --- | --- | --- |
| PostgreSQL | Relational database | See the reasons below. The same locks (`SELECT ... FOR UPDATE`) and transactions as MySQL keep trades free of lost or duplicated cards. |
| Jackson | Conversion between Java objects and JSON | Serialises the battle replay (turn-by-turn log and deck copies) stored in `jsonb` columns, and sends it to the frontend as it is. It is already included in Spring Web. |

What PostgreSQL offers that the schema actually uses:

- **`jsonb`**: the battle log and the deck copies are stored in a binary format that is faster to read and can be queried if needed later.
- **Unique index on expressions**: a friendship between A and B cannot exist twice in opposite directions, thanks to an index on `LEAST` and `GREATEST` of the two player ids.
- **`timestamptz`**: timestamps are stored with their time zone, so deadlines (Wanderers after 7 days, trades after 48 hours, FRIEND challenges after 30 seconds, protection after attacks, the opponent list cooldown) do not depend on the server's time zone.

PostgreSQL is free and as well supported by Spring Boot as MySQL. The switch from MySQL has been confirmed.
