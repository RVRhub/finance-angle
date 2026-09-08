# Finance Angle Agent Rules

These rules apply to all AI-assisted changes in this repository.

## Global workflow
- Work on a feature/fix/chore branch; never push AI-generated feature work directly to `main`.
- Keep each change focused on one feature or fix. Avoid unrelated refactoring.
- Inspect the relevant implementation and tests before editing behavior.
- Prefer existing project patterns over new abstractions, libraries, plugins, or services.
- Preserve public API and persisted-data compatibility unless the requested change explicitly requires otherwise.
- Never commit credentials, tokens, private keys, or real customer data.

## Quality gates
- Add or update tests for business logic, financial calculations, persistence behavior, API behavior, and bug fixes.
- Run the narrowest relevant tests while iterating and `./gradlew test` before declaring a PR ready when the environment allows it.
- Never claim checks passed unless they were actually executed.
- Review the final diff for accidental, generated, or unrelated changes.
- Keep PRs small when practical. If a change grows beyond roughly 5 files or 400 changed lines, split it or explain the scope.

## Pull requests
- PRs must state scope, testing evidence, risk, API/database impact, and known limitations.
- Treat financial calculations, database migrations, authentication/authorization, and public API contracts as high-risk changes requiring explicit human review before merge.

## Module-specific rules
Read the nearest module `AGENTS.md` before changing code in that module:
- `backend/AGENTS.md`
- `dashboard-app/AGENTS.md`
- `mcp-server/AGENTS.md`
