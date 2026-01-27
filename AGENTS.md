# Repository Guidelines

## Project Structure & Module Organization
- `backend/` is the Spring Boot API (Kotlin); controllers/services live in `src/main/kotlin`, configuration in `application.yml`, and Flyway migrations in `src/main/resources/db/migration`.
- `backend/src/test/kotlin` holds integration and unit tests using H2 and Spring Boot testing utilities.
- `mcp-server/` is the Ktor-based MCP bridge exposing Finance Angle APIs; code is in `src/main/kotlin` and tests in `src/test/kotlin` with MockWebServer fixtures.
- `docker-compose.yml` connects Postgres (`db`), the API (`app`), and the MCP server (`mcp`); the shared Gradle wrapper sits at the repo root.

## Build, Test, and Development Commands
- `./gradlew clean build` – compile all modules and run their tests.
- `./gradlew :backend:bootRun` – start the API locally (requires datasource env vars).
- `./gradlew :mcp-server:run` – run the MCP server against a running API.
- `docker compose up --build db app` – launch Postgres + API containers; schema auto-migrated via Flyway.
- `docker compose run --rm mcp` – start the MCP bridge within the Compose network.
- `docker compose logs -f app` – follow application logs while iterating.

## Coding Style & Naming Conventions
- Kotlin with 4-space indentation; keep idiomatic null-safety and data classes for payloads/records.
- Package names are lowercase dot-separated; classes/objects use PascalCase, functions/fields use camelCase, constants use uppercase snake_case.
- Keep controllers thin, move business logic into services, and validate request DTOs with javax validation annotations in the API module.
- Favor small, single-purpose functions and clear constructor injection.

## Testing Guidelines
- Backend tests use JUnit 5 + Spring Boot testing + H2; name files `*Test.kt` and prefer descriptive `fun should...()` methods.
- MCP tests rely on `kotlin.test` and `MockWebServer`; keep network calls under test predictable with recorded fixtures.
- Run all tests via `./gradlew test`; target a module with `./gradlew :backend:test` or `./gradlew :mcp-server:test`.
- Add regression tests alongside new endpoints or MCP tools to lock behavior before refactoring.

## Commit & Pull Request Guidelines
- Commit messages follow short, imperative summaries (e.g., “Fix MCP connection to ChatGPT”); group related changes and include schema or config context when relevant.
- PRs should state scope, testing performed, and any required env vars or migrations; for API updates, include sample requests/responses and note new endpoints.
- Keep diffs focused, update README/docs when workflow steps change, and ensure Compose + Gradle commands still succeed.

## Security & Configuration Tips
- Never commit credentials; supply `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` locally or rely on Compose defaults. For MCP, configure `FINANCE_ANGLE_BASE_URL` when not using Compose.
- Flyway runs on startup; verify migrations match Postgres version (15) and avoid destructive changes without backups.
- Use docker volume `postgres-data` only for local persistence; drop/recreate carefully if schema resets are needed.
