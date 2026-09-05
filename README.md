# Finance Angle

Finance Angle is a Kotlin + Spring Boot playground to capture daily spending, track savings snapshots, and explore AI-assisted budgeting ideas.

## Getting started

1. Ensure Java 21 is installed.
2. Configure Postgres (or use a local Docker instance) and export datasource credentials, for example:
   ```bash
   export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/finance_angle
   export SPRING_DATASOURCE_USERNAME=finance_angle
   export SPRING_DATASOURCE_PASSWORD=finance_angle
   ```
3. If `gradle` is available locally, regenerate the wrapper to keep scripts up to date:
   ```bash
   gradle wrapper --gradle-version 8.7
   ```
   Otherwise, run the backend with the provided wrapper:
   ```bash
   ./gradlew :backend:bootRun
   ```
4. Access the API at `http://localhost:8080`.

## Containerised dev stack

You can run the service, its Postgres dependency, and the MCP bridge via Docker Compose:

1. Build and start Postgres + API:
   ```bash
   docker compose up --build db app
   ```
   The API becomes available on `http://localhost:8080` once Flyway migrates the schema.
2. In a separate terminal, open the logs:
   ```bash
   docker compose logs -f app
   ```

### MCP server for ChatGPT Dev Mode

The repository contains `backend/` (Spring Boot API) and `mcp-server/` (Kotlin MCP bridge). The MCP server exposes Finance Angle APIs through the Model Context Protocol.

1. Build the bridge container:
   ```bash
   docker compose build mcp
   ```
2. Point the ChatGPT Dev Mode MCP command to:
   ```bash
   docker compose run --rm mcp
   ```
3. Ensure `db` and `app` are running so the bridge can reach `http://app:8080`.
4. For local debugging outside containers:
   ```bash
   ./gradlew :mcp-server:run
   ```

## API outline

- `POST /api/transactions` – add a transaction (manual or AI-parsed).
- `GET /api/transactions/summary` – aggregate totals for a selected period.
- `POST /api/receipts/ingest` – register a receipt/photo ingestion event.
- `GET /api/receipts/{externalId}` – check ingestion status.
- `POST /api/savings/snapshots` – log current savings.
- `GET /api/savings/snapshots/latest` – fetch the last savings snapshot.
- `GET /api/insights/recommendations` – return AI guidance (placeholder until configured).

## Dashboard app (SQLite + ECharts)

Run the lightweight dashboard module with SQLite file storage:

```bash
DB_FILE=/path/to/iCloud/finance.db ./gradlew :dashboard-app:bootRun
```

Open:

- `http://localhost:8090` for the interactive ECharts dashboard.
- `http://localhost:8090/swagger-ui.html` for CSV upload and manual entries.

The chart runtime is packaged locally as a WebJar, so the browser does not depend on a CDN. The dashboard reads JSON from:

- `GET /api/account-positions/comparison`
- `GET /api/summary/spending?months=12`
- `GET /api/snapshots`

Charts support responsive resizing, hover values, legend filtering, timeline zooming, reset, and image export.

Create or replace a historical monthly position with `POST /api/account-positions/monthly` and a body such as:

```json
{
  "month": "2026-08",
  "savingsBudget": {
    "amount": 500,
    "currency": "EUR"
  }
}
```

The legacy `/api/charts/*.svg` endpoints remain available temporarily while consumers migrate.

## Next steps

- Replace the `NoOpAiClient` with a real OpenAI implementation.
- Wire ChatGPT voice/photo workflows to the existing ingestion and transaction endpoints.
- Add budgets, alerts, predictions, and richer dashboard filters.
- Remove Lets-Plot and the legacy SVG endpoints after the ECharts migration is verified.
