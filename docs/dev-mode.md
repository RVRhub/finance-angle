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
This launches Postgres (`db`) and the Spring Boot API (`app`). Wait until the app reports `Started FinanceAngleApplication` and Flyway has applied migrations.

## 2. Run the MCP bridge locally
The bridge lives in `mcp-server`. Build it once:
```bash
./gradlew :mcp-server:installDist
```
Then start it (in another terminal) with logging enabled:
```bash
MCP_LOG_LEVEL=DEBUG \
FINANCE_ANGLE_BASE_URL=http://localhost:8080 \
mcp-server/build/install/mcp-server/bin/mcp-server
```
Leave this running while you test; it prints JSON-RPC logs to stderr and emits responses to stdout.

### Quick local sanity check (optional)
Use the shell snippet below to send the canonical `initialize` and `tools/list` requests via stdio:
```bash
coproc MCP (MCP_LOG_LEVEL=INFO FINANCE_ANGLE_BASE_URL=http://localhost:8080 \
            mcp-server/build/install/mcp-server/bin/mcp-server)

send_rpc() {
  local json="$1"
  local bytes=${#json}
  printf 'Content-Length: %d\r\n\r\n%s' "$bytes" "$json" >&${MCP[1]}
}

read_rpc() {
  local header line
  while IFS= read -r -u ${MCP[0]} line; do
    header+="$line"$'\n'
    [[ "$line" == $'\r' ]] && break
  done
  local length=$(echo "$header" | awk -F': ' 'BEGIN{IGNORECASE=1}/Content-Length/{print $2}')
  IFS= read -r -N "${length%$'\r'}" -u ${MCP[0]} body
  echo "$body"
}

send_rpc '{"jsonrpc":"2.0","id":1,"method":"initialize"}'
read_rpc
send_rpc '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
read_rpc

kill ${MCP_PID:-${MCP[0]}}
```
You should see the server metadata plus a list of tools (e.g., `createTransaction`, `registerReceipt`).

## 3. Attach the bridge to Dev Mode (ChatGPT UI)
1. Open the Dev Mode sidebar → **Add MCP Server**.
2. Enter a descriptive name (e.g., “Finance Angle MCP”).
3. Provide the command ChatGPT should execute for each session. Two common options:
   - **Direct binary (local CLI session):** `mcp-server/build/install/mcp-server/bin/mcp-server`
   - **Docker wrapper (containerised):** `docker compose run --rm mcp`
4. Set environment variables if the backend runs at a non-default location:
   - `FINANCE_ANGLE_BASE_URL=http://host.docker.internal:8080`
   - `MCP_LOG_LEVEL=INFO`
5. Save. Dev Mode will probe the bridge and show the available tools when successful.

## 4. Test the workflow end-to-end
1. Start a Dev Mode chat and invoke the `createTransaction` tool manually to verify connectivity.
2. Provide a natural-language instruction (e.g., "Log yesterday's grocery run for 24.50 EUR"). The GPT should confirm details then call the tool.
3. Check backend logs (`docker compose logs -f app`) to confirm the request arrived.

## 5. Optional: Expose the bridge to Custom GPTs
To call the same MCP tools from a Custom GPT instead of Dev Mode:
1. Expose the bridge via a tunnel (`ssh -R`, `cloudflared`, `ngrok`, etc.) and note the public URL.
2. In the Custom GPT builder → **Actions** → **Add Action** → **Model Context Protocol**, supply the tunneled endpoint and any required headers.
3. Reuse the environment variables above so the bridge points at the correct backend.
4. Save and test the action inside the custom GPT chat.

## 6. HTTP mode (for MCP Server URL workflows)
If you prefer to connect through an HTTP endpoint—useful when Dev Mode isn’t available—run the MCP bridge with the bundled HTTP wrapper:

```bash
./gradlew :mcp-server:installDist
MCP_HTTP_PORT=3333 \
FINANCE_ANGLE_BASE_URL=http://localhost:8080 \
mcp-server/build/install/mcp-server/bin/http-mcp-server
```

The server exposes:
- `GET /health` – simple liveness check (`200 ok`).
- `POST /` – JSON-RPC 2.0 requests with the same payloads as the stdio transport.

To share it with ChatGPT:
1. Tunnel the port with ngrok (requires a free ngrok account/token):
   ```bash
   ngrok http 3333
   ```
   ngrok prints an HTTPS forwarding URL (for example `https://abcd-1-2-3-4.ngrok-free.app`). Leave ngrok running while ChatGPT uses the bridge.
2. Copy the HTTPS forwarding URL.
3. In the Custom GPT builder → **Actions** → **Add Action** → **Model Context Protocol**, paste the URL into **Server URL**.
4. Set `FINANCE_ANGLE_BASE_URL` (and optional `MCP_LOG_LEVEL`) in the "Environment Variables" section so the bridge resolves backend requests correctly.

### Quick curl checks
Before tunnelling:
```bash
# Metadata (Custom GPTs sometimes call GET / before POSTing JSON-RPC)
curl http://localhost:3333/

# Health probe
curl http://localhost:3333/health

# JSON-RPC tool list
curl -X POST http://localhost:3333/ \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

# Example tool call (register receipt)
curl -X POST http://localhost:3333/ \
  -H 'Content-Type: application/json' \
  -d '{
        "jsonrpc": "2.0",
        "id": 2,
        "method": "tools/call",
        "params": {
          "toolName": "registerReceipt",
          "arguments": {
            "externalId": "curl-receipt-001",
            "status": "PENDING"
          }
        }
      }'
```

After ngrok is running, repeat the same `curl` commands with the forwarding URL:
```bash
curl https://<your-ngrok-url>/
curl https://<your-ngrok-url>/health
curl -X POST https://<your-ngrok-url>/ -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```
If these calls succeed, the Custom GPT integration will work as well.

ChatGPT now calls the HTTP endpoint directly without needing Dev Mode. Logs still stream to stderr; set `MCP_LOG_LEVEL=DEBUG` when diagnosing issues.

## Notes
- The MCP server is stateless; stop the container after each session.
- Ensure Postgres data persists via the `postgres-data` volume defined in `docker-compose.yml`.
- Any backend changes require rebuilding containers: `docker compose build app mcp`.
