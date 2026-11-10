# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Product

WORKHUB is a multi-tenant Agile/Scrum project management platform. The full business spec (Vietnamese) is [docs/requirements.md](docs/requirements.md); read the relevant section before building a module. It also defines the milestone order (M0–M11).

Domain hierarchy: User → Workspace → Team → Project → (Backlog/Issues, Sprints, Board, Documents, Meetings, Reports, Chat/Notifications). A user can belong to many Workspaces, Teams and Projects.

Decisions and rules that shape the code:
- **Modular monolith.** One Spring Boot app; each domain module (auth, workspace, team, project, issue, sprint, ...) gets its own package under the base package.
- **Scoped RBAC.** Roles are assigned per Workspace, Team or Project membership, never as one global role per user. Scrum responsibilities (Product Owner, Scrum Master, Developer) are project-scoped. Every permission check must be enforced in the backend; hiding UI is not enough.
- **History is data.** Burndown/Burnup/Velocity must be rebuilt from recorded history (scope added/removed, status transitions, daily snapshots), so sprint scope changes and issue status changes must be persisted as events/snapshots, not just overwritten. Sprint Backlog keeps its scope at sprint start.
- **Product Backlog ≠ Sprint Backlog.** Backlog is project-scoped in the MVP; one Scrum Team per Project in the MVP.
- Sprint status: Future → Active → Completed (or Cancelled), max one month. Ending a sprint never auto-marks issues Done; unfinished issues go back to the backlog or the next sprint.
- Story Points are relative size, separate from time estimates (Original/Remaining Estimate).
- Realtime is WebSocket; collaborative document editing must use a CRDT (Yjs), not whole-document overwrite.

## Layout

Monorepo with two independent projects and no root build tool:

- `backend/` — Spring Boot 4.1 (Java 21, Maven wrapper). Base package `com.workhub`, one package per domain module (`user/`, `security/`, ...), not per layer. `hello/` is a public sample endpoint that can be deleted once real features exist.
- `frontend/` — React 19 + Vite + TypeScript SPA.
- `docker-compose.yml` — PostgreSQL 17 for local development, published on host port **5433** (override with `POSTGRES_PORT`), because 5432 is taken by another project's database on this machine.

Run backend commands from `backend/` and frontend commands from `frontend/`.

## Commands

Backend (`backend/`):

```sh
./mvnw spring-boot:run                                   # API on http://localhost:8080 (H2 in-memory)
SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run   # use PostgreSQL (start it first: docker compose up -d, from repo root)
./mvnw test                                              # all tests
./mvnw test -Dtest=SecurityTests                         # one test class
./mvnw test -Dtest='SecurityTests#protectedEndpointRejectsMissingToken'   # one test method (quote it in zsh)
./mvnw clean test                                        # use after renaming/moving classes; stale classes in target/ still run otherwise
./mvnw package                                           # build jar into target/
```

Frontend (`frontend/`):

```sh
npm run dev       # http://localhost:5173
npm run build     # type-check (tsc -b) + production build into dist/
npm run lint      # oxlint (not ESLint)
```

The frontend has no test runner configured yet.

## How the pieces connect

- The frontend calls the backend with relative `/api/...` URLs. In dev, the Vite proxy in `frontend/vite.config.ts` forwards `/api` to `localhost:8080`, so no CORS configuration exists in the backend. Keep backend REST endpoints under `/api` so the proxy covers them; adding CORS or absolute URLs is only needed if the apps are served from different origins in production.
- Database selection is profile-driven:
  - Default profile (`application.properties`): in-memory H2 in PostgreSQL mode, H2 console at `/h2-console`, data lost on restart. Tests run on this profile, so they need no Docker.
  - `postgres` profile (`application-postgres.properties`): reads `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, defaulting to the `docker-compose.yml` database.
- **Schema is owned by Flyway.** Every schema change is a new file `backend/src/main/resources/db/migration/V<n>__<description>.sql`; never edit a migration that has been committed. Hibernate runs with `ddl-auto=validate`, so the app fails to start if entities and tables disagree. Migrations run on both H2 and PostgreSQL, so write portable SQL; generate UUID ids in the application (`GenerationType.UUID`), not with database functions.
- **Authentication is stateless JWT** (`security/`). `JwtService` issues HS256 access tokens (subject = user id, `email` claim, 15 min TTL); Spring Security's OAuth2 resource server validates the `Authorization: Bearer` header. Everything is authenticated except `PUBLIC_PATHS` in `SecurityConfig` (`/api/auth/**` is reserved for register/login/refresh). Read the caller with `@AuthenticationPrincipal Jwt jwt` (see `MeController`). Config lives under `workhub.jwt.*`; set `JWT_SECRET` (≥ 32 bytes) outside local development. Passwords use the `PasswordEncoder` bean (delegating, bcrypt by default). Refresh tokens are not built yet (M1).
- User emails are stored lower-cased; normalise before saving or querying.
- Actuator is enabled; `/actuator/health` is the public health check.

## Spring Boot 4 notes

This project is on Spring Boot 4, where starters are split per feature and packages moved. For example, `@WebMvcTest`/`@AutoConfigureMockMvc` live in `org.springframework.boot.webmvc.test.autoconfigure`, and the JWT starter is `spring-boot-starter-security-oauth2-resource-server`. Check imports and starter names against Boot 4 (e.g. via start.spring.io) when adapting older examples.

Controller tests that go through security use `@SpringBootTest` + `@AutoConfigureMockMvc` (see `SecurityTests`), so the real `SecurityConfig` applies.

The machine's JDK is 23; the build targets Java 21 (`java.version` in `pom.xml`).
