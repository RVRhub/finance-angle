# MCP Server Agent Rules

Apply these rules to changes under `mcp-server/`.

- Keep the MCP layer thin: expose Finance Angle capabilities without duplicating backend business logic.
- Preserve existing tool contracts unless a breaking change is explicitly requested and documented.
- Keep external/API interactions deterministic under test using the existing MockWebServer approach.
- Do not expose secrets, credentials, raw internal errors, or unnecessary backend data through MCP responses.
- Prefer extending existing tools and transport patterns over adding new frameworks or infrastructure.
- Add regression tests for new or changed MCP tools and error-handling paths.
- Useful commands: `./gradlew :mcp-server:test`, `./gradlew :mcp-server:run`.
