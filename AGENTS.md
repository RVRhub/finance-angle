# Repository Guidelines

## Project Structure & Module Organization
- `backend/` is the Spring Boot API (Kotlin); controllers/services live in `src/main/kotlin`, configuration in `application.yml`, and Flyway migrations in `src/main/resources/db/migration`.
- `backend/src/test/kotlin` holds integration and unit tests using H2 and Spring Boot testing utilities.
- `mcp-server/` is the Ktor-based MCP bridge exposing Finance Angle APIs; code is in `src/main/kotlin` and tests in `src/test/kotlin` with MockWebServer fixtures.
- `dashboard-app/` contains the dashboard UI and its supporting backend code.
- `docker-compose.yml` connects Postgres (`db`), the API (`app`), and the MCP server (`mcp`); the shared Gradle wrapper sits at the repo root.

## Build, Test, and Development Commands
- `./gradlew clean build` – compile all modules and run their tests.
- `./gradlew test` – run the full test suite.
- `./gradlew :backend:bootRun` – start the API locally (requires datasource env vars).
- `./gradlew :mcp-server:run` – run the MCP server against a running API.
- `docker compose up --build db app` – launch Postgres + API containers; schema auto-migrated via Flyway.
- `docker compose run --rm mcp` – start the MCP bridge within the Compose network.
- `docker compose logs -f app` – follow application logs while iterating.

## Coding Style & Naming Conventions
- Kotlin with 4-space indentation; keep idiomatic null-safety and data classes for payloads/records.
- Package names are lowercase dot-separated; classes/objects use PascalCase, functions/fields use camelCase, constants use uppercase snake_case.
- Keep controllers thin, move business logic into services, and validate request DTOs with validation annotations in the API module.
- Favor small, single-purpose functions and clear constructor injection.
- Prefer existing project patterns over introducing new abstractions or dependencies.

## AI Development Guardrails
These rules apply to AI-assisted changes and are also good defaults for human contributors.

### Before changing code
- Read this file and inspect the relevant implementation and tests before editing.
- Understand the existing data flow and architecture before introducing a new abstraction.
- Keep the change scoped to the requested feature or fix; do not bundle unrelated cleanup or refactoring.
- Do not add a new library, framework, build plugin, or external service unless the PR clearly explains why the existing stack is insufficient.

### While implementing
- One feature or fix should normally map to one focused pull request.
- Preserve public API and persisted-data compatibility unless the change explicitly requires otherwise.
- Add or update regression tests whenever business logic, financial calculations, API behavior, persistence behavior, or bug fixes change.
- Do not duplicate financial/business calculations in the dashboard if the backend already owns that logic.
- Never use `Float` or `Double` for monetary calculations. Use `BigDecimal` and retain currency information where the domain model requires it.
- Treat monetary sign conventions as business rules: assets/savings and liabilities/debts must not silently change meaning.
- BigDecimal assertions must compare values intentionally; do not rely on scale-sensitive equality when scale is not part of the business rule.
- Database schema changes require a Flyway migration. Avoid destructive migrations by default and document any data-loss risk.
- Never commit credentials, tokens, private keys, real customer data, or secrets.

### Before opening a pull request
- Review the final diff and remove accidental, generated, or unrelated changes.
- Run the narrowest relevant tests while iterating, then run `./gradlew test` before declaring the change ready when the environment allows it.
- If tests cannot be run, state that explicitly in the PR; never claim unexecuted checks passed.
- Document scope, risk, test evidence, database/API impact, and any known limitations.
- Prefer small diffs. If a change grows beyond roughly 5 files or 400 changed lines, consider splitting it or explain why the larger scope is necessary.

## Testing Guidelines
- Backend tests use JUnit 5 + Spring Boot testing + H2; name files `*Test.kt` and prefer descriptive `fun should...()` methods.
- MCP tests rely on `kotlin.test` and `MockWebServer`; keep network calls under test predictable with recorded fixtures.
- Run all tests via `./gradlew test`; target a module with `./gradlew :backend:test` or `./gradlew :mcp-server:test`.
- Add regression tests alongside new endpoints or MCP tools to lock behavior before refactoring.
- Financial calculations require tests for positive, negative, zero, and scale-sensitive monetary cases where applicable.

## Commit & Pull Request Guidelines
- Never push AI-generated feature work directly to `main`; use a feature/fix/chore branch and a pull request.
- Commit messages follow short, imperative summaries (e.g., “Fix MCP connection to ChatGPT”); group related changes and include schema or config context when relevant.
- PRs should state scope, testing performed, risk, and any required env vars or migrations; for API updates, include sample requests/responses and note new endpoints.
- Keep diffs focused, update README/docs when workflow steps change, and ensure Compose + Gradle commands still succeed.
- Treat changes to financial calculations, database migrations, authentication/authorization, and public API contracts as high risk and require explicit human review before merge.

## Security & Configuration Tips
- Never commit credentials; supply `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` locally or rely on Compose defaults. For MCP, configure `FINANCE_ANGLE_BASE_URL` when not using Compose.
- Flyway runs on startup; verify migrations match Postgres version (15) and avoid destructive changes without backups.
- Use docker volume `postgres-data` only for local persistence; drop/recreate carefully if schema resets are needed.
