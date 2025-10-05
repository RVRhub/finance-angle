# Finance Angle Requirements & Task Backlog

## Functional Requirements
- Record transactions from receipts, voice/chat inputs, and manual entries with rich metadata (amount, currency, merchant, category, tags, notes).
- Maintain a registry of receipt ingestion events and link them to transactions.
- Log savings snapshots with timestamps, account labels, balances, and optional notes.
- Provide summaries across weekly, monthly, quarterly, and yearly periods with category breakdowns.
- Offer AI-generated insights: parsing unstructured inputs, summarising spending, and recommending savings allocations.
- Support reminders for salary events and overdue savings confirmations.

## Non-Functional Requirements
- Ensure data validation and idempotency for repeated submissions.
- Default currency EUR but allow extension to other currencies later.
- Respect privacy by limiting artifact retention and marking sensitive notes.
- Provide audit trails with trace IDs for agent actions.
- Deliver quick API responses (<500 ms typical) and graceful error handling with meaningful messages.
- Keep backend configurable via environment variables for database and AI credentials.

## Initial Task Backlog
| ID | Description | Type | Priority | Status |
| -- | ----------- | ---- | -------- | ------ |
| T-01 | Define transaction DTOs, validation rules, and persistence schema | Feature | High | Todo |
| T-02 | Implement receipt ingestion endpoint and metadata store | Feature | High | Todo |
| T-03 | Build savings snapshot API and database tables | Feature | High | Todo |
| T-04 | Create summary aggregation service covering week/month/quarter/year | Feature | High | Todo |
| T-05 | Add AI parsing pipeline prototype for voice/photo inputs | Research | Medium | Todo |
| T-06 | Draft forecasting stub and budget comparison alerts | Feature | Medium | Todo |
| T-07 | Wire reminder workflow for salary and savings confirmations | Feature | Medium | Todo |
| T-08 | Establish automated tests (unit, integration) for core flows | Quality | High | Todo |
| T-09 | Document security/privacy approach for artifacts and notes | Documentation | Medium | Todo |
| T-10 | Prepare deployment checklist and CI pipeline | DevOps | Medium | Todo |

## Acceptance Criteria Snapshot
- Transactions cannot be recorded without category and amount; invalid requests receive clear 4xx errors.
- Receipt ingestion returns an `externalId` and stores timestamps for audits.
- Savings snapshot history is queryable and sorted chronologically.
- Summary endpoint returns totals per category plus overall spend for requested windows.
- AI parsing delivers structured payloads with confidence scores or explicit escalation flags.
- Reminder service schedules notifications and avoids duplicate pings within the defined interval.

## Tracking & Updates
- Update status column as work progresses; add new IDs for backlog items.
- Link to design docs or tickets when detailing complex tasks.
- Sync with `docs/project-plan.md` during milestone reviews.
