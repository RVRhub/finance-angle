# ChatGPT Dev Mode Setup

This guide explains how to connect the Finance Angle backend to ChatGPT Dev Mode via the Kotlin MCP bridge.

## Prerequisites
- Docker Desktop (or compatible runtime).
- ChatGPT Dev Mode enabled in your OpenAI account.
- Checked-out Finance Angle repository.

## 1. Start core services
```bash
docker compose up --build db app
```
This command launches Postgres (`db`) and the Spring Boot API (`app`). Wait for the logs to show `Started FinanceAngleApplication` before continuing.

## 2. Launch the MCP bridge on demand
When Dev Mode starts a session, it must run the MCP server as a process with STDIN/STDOUT attached. Use Docker Compose to spin up a disposable container that connects to the already running backend:
```bash
docker compose run --rm mcp
```
Keep this command handy; ChatGPT Dev Mode can be configured to run it automatically for each session.

## 3. Configure Dev Mode (ChatGPT UI)
1. Open the Dev Mode sidebar → **Add MCP Server**.
2. For **Command**, use `docker compose run --rm mcp`.
3. Optionally supply environment variables if you run the backend elsewhere:
   - `FINANCE_ANGLE_BASE_URL=http://host.docker.internal:8080`
4. Save the server. ChatGPT now discovers the exposed tools (`createTransaction`, `registerReceipt`, etc.) and can call them during conversations.

## 4. Test the workflow
1. Start a Dev Mode chat and invoke the `createTransaction` tool manually to verify connectivity.
2. Provide a natural-language instruction (e.g., "Log yesterday's grocery run for 24.50 EUR"). The GPT should confirm details then call the tool.
3. Check backend logs (`docker compose logs -f app`) to confirm the request arrived.

## Notes
- The MCP server is stateless; stop the container after each session.
- Ensure Postgres data persists via the `postgres-data` volume defined in `docker-compose.yml`.
- Any backend changes require rebuilding containers: `docker compose build app mcp`.
