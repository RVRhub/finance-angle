# Finance Angle Agent Guide

This guide outlines how automated agents should interact with the Finance Angle system to ingest transactions, capture savings snapshots, and generate insights safely.

## Mission
- Maintain an up-to-date ledger of day-to-day spending from photos, voice transcripts, or manual notes.
- Track real savings balances so forecasts stay grounded in actual deposits.
- Surface summaries and recommendations that help users plan upcoming expenses.

## Core Responsibilities
### 1. Transaction Ingestion
- **Inputs**: receipt photos, chat/voice statements (e.g., "Bought 2.5 kg potatoes for 10 euro"), or manual form entries.
- **Output**: `POST /api/transactions` payload containing amount, currency (default `EUR`), date/time, merchant, category (`food`, `entertainment`, `family`, etc.), tags, and optional notes.
- **Link Receipts**: For photo uploads, call `POST /api/receipts/ingest` first to register the artifact, then include the returned `externalId` in the transaction.
- **Storage**: Keep heavy artifacts (images/audio) on the user's device; backend receives only metadata.

### 2. Savings Snapshots
- Prompt users after salary events to record transfers into savings wallets.
- Use `POST /api/savings/snapshots` with timestamp, account label, and balance at least once or twice per month.
- Remind users to confirm real balances so forecasts reflect actual assets.

### 3. Insight Generation
- Aggregate spending via `GET /api/transactions/summary` across weekly, monthly, quarterly, and yearly ranges.
- Provide category-level breakdowns with trend notes and highlight budget adherence or overruns.
- Suggest planned expenses and alert when savings deposits are overdue.

## Data Contracts
- Transactions: amount, currency, occurredAt, merchant, category, tags, notes, optional `receiptExternalId`.
- Receipt ingestion: external ID, ingestion status, timestamps.
- Savings snapshot: account label, capturedAt, balance, optional notes.
- Follow JSON schemas defined in backend DTOs when available; reject unknown fields.

## Operational Rules
- Validate inputs (e.g., positive amounts, supported currencies) before calling APIs.
- If parsing fails, escalate to a human with the raw artifact attached.
- Use idempotent logic where possible (deduplicate repeated photo uploads).
- Respect privacy: do not retain copies of artifacts beyond session scope; defer to backend retention policy once defined.
- Log actions with trace IDs so humans can audit the agent trail.

## Guardrails & Escalation
- Missing category mapping → flag for human classification rather than guessing wildly.
- Contradictory amounts between photo and voice → ask user to confirm.
- API failure (4xx/5xx) → retry once, then notify a human with context.
- Sensitive notes (medical, legal) → mark appropriately for additional review.

## Tooling Checklist
- Environment variables: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
- Startup: `./gradlew bootRun`.
- Health check: `GET /actuator/health` (if enabled) or `GET /api/transactions/summary` with a small date range.

## Future Extensions (Stay Aware)
- Multi-currency support with conversion rules.
- Shared family accounts requiring consent flows.
- CSV/accounting exports.
- Advanced forecasting (seasonal, anomaly detection).

## Related Docs
- `docs/product-context.md` – overall vision and open questions.
- Backend API source files under `src/main/kotlin/...` for request/response definitions.
