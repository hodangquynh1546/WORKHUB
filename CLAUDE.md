# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Layout

Monorepo with two independent projects and no root build tool:

- `backend/` — Spring Boot 4.1 (Java 21, Maven wrapper). Base package `com.projectinvidial.backend`, organised by feature (e.g. `hello/`), not by layer.
- `frontend/` — React 19 + Vite + TypeScript SPA.
- `docker-compose.yml` — PostgreSQL 17 for local development.

Run backend commands from `backend/` and frontend commands from `frontend/`.

## Commands

Backend (`backend/`):

```sh
./mvnw spring-boot:run                                   # API on http://localhost:8080 (H2 in-memory)
SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run   # use PostgreSQL (start it first: docker compose up -d, from repo root)
./mvnw test                                              # all tests
./mvnw test -Dtest=HelloControllerTests                  # one test class
./mvnw test -Dtest='HelloControllerTests#greetsByName'   # one test method (quote it in zsh)
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
  - Default profile (`application.properties`): in-memory H2, H2 console at `/h2-console`, data lost on restart. Tests run on this profile, so they need no Docker.
  - `postgres` profile (`application-postgres.properties`): reads `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, defaulting to the credentials in `docker-compose.yml`.
- Schema is managed by `spring.jpa.hibernate.ddl-auto=update`; there is no migration tool (Flyway/Liquibase) yet.
- Actuator is enabled; `/actuator/health` is the health check.

## Spring Boot 4 notes

This project is on Spring Boot 4, where test support is split into per-feature starters and packages moved. For example, `@WebMvcTest` is `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` (from `spring-boot-starter-webmvc-test`), not the Boot 3 `org.springframework.boot.test.autoconfigure.web.servlet` package. Check imports against Boot 4 when adapting older examples.

The machine's JDK is 23; the build targets Java 21 (`java.version` in `pom.xml`).
