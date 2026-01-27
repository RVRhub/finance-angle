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

1. Build and start Postgres + the Spring Boot API:
   ```bash
   docker compose up --build db app
   ```
   The API becomes available on `http://localhost:8080` once Flyway migrates the schema.
2. In a separate terminal you can open the logs or run disposable commands:
   ```bash
   docker compose logs -f app
   ```

### MCP server for ChatGPT Dev Mode

The repository now contains two modules: `backend/` (Spring Boot API) and `mcp-server/` (Kotlin MCP bridge). The `mcp-server/` module exposes Finance Angle APIs through the Model Context Protocol so ChatGPT Dev Mode can drive all interactions.

1. Build the bridge container (runs as needed by Compose):
   ```bash
   docker compose build mcp
   ```
2. When configuring Dev Mode in ChatGPT, point the MCP server command to:
   ```bash
   docker compose run --rm mcp
   ```
   This command streams STDIO between ChatGPT and the MCP server while reusing the already running API container.
3. Ensure `db` and `app` services are running before launching the MCP server so tool calls can reach `http://app:8080` inside the Compose network.
4. For local debugging outside containers you can also run the bridge directly:
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
- `GET /api/insights/recommendations` – returns AI guidance (placeholder until configured).

## Next steps

- Replace the `NoOpAiClient` with a real OpenAI implementation.
- Wire ChatGPT voice/photo workflows to call the existing ingestion and transaction endpoints.
- Extend the summary reporting with budgets, alerts, and prediction visualisations.

## Dashboard app (SQLite + charts)

- Run the lightweight dashboard module locally with SQLite file storage:
  ```bash
  DB_FILE=/path/to/iCloud/finance.db ./gradlew :dashboard-app:bootRun
  ```
- Open the UI at `http://localhost:8090` for charts or `http://localhost:8090/swagger-ui.html` for CSV upload and manual entries.
