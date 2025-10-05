# Finance Angle Stack & Operating Rules

## Core Technology Stack
- **Backend**: Kotlin 1.9+ with Spring Boot 3.x.
- **Database**: PostgreSQL 14+; use Flyway or Liquibase for schema migrations.
- **Build Tooling**: Gradle (wrapper included); target Java 21 runtime.
- **AI Integration**: Replace `NoOpAiClient` with OpenAI-compatible client (gRPC/HTTP) when credentials available.
- **Testing**: JUnit 5, MockK, and Testcontainers for integration with Postgres.

## Coding Conventions
- Follow Kotlin style guides (official formatting); enforce with `ktlint` or detekt in CI.
- Package structure by domain: `transactions`, `receipts`, `savings`, `insights`.
- DTOs should live alongside controllers; mappers handle conversions to persistence models.
- Use sealed classes or enums for categories to ensure consistent tagging.
- Write comprehensive unit tests for service logic and contract tests for controllers.

## API Practices
- RESTful endpoints under `/api/*` with clear versioning if breaking changes arise.
- Validate request payloads with Spring Validation annotations; return problem detail payloads on error.
- Expose OpenAPI/Swagger docs for agent consumption.
- Log request IDs and user/agent identifiers for traceability.

## Data & Storage Rules
- Store only metadata for receipts in backend; binary artifacts remain on client devices unless retention policy changes.
- Encrypt sensitive fields at rest where possible (Postgres column encryption or application-level).
- Maintain history tables for auditing critical changes (transactions, savings snapshots).

## Deployment & Operations
- Provide containerized runtime (Dockerfile) aligned with Java 21.
- Use environment variables for configuration; no secrets in source control.
- Monitor via Spring Actuator metrics and logs; forward to centralized observability stack (e.g., Grafana/Prometheus).
- Implement feature flags for experimental AI flows.

## Agent-Specific Rules
- Agents must authenticate using dedicated credentials with scoped permissions.
- All automated writes should include `agentId` metadata for downstream audits.
- Retry API interactions with exponential backoff; escalate after max 2 retries.
- Respect user privacy settings (e.g., skip storing note text if flagged confidential).
- Update documentation (`docs/agents.md`) whenever new behaviours are introduced.

## Future Considerations
- Evaluate support for mobile-first ingestion pipelines and offline caching.
- Investigate end-to-end encryption for receipts and transcripts.
- Plan for horizontal scaling (connection pooling, stateless services).
