# Finance Angle Delivery Plan

## Phase 0 – Foundations
- Finalize product context, agent guide, and stack rules to align humans and agents.
- Stand up core infrastructure: Postgres database, Spring Boot service, and baseline CI checks.
- Define DTOs for transactions, receipts, and savings snapshots with validation tests.
- Deliverable: running backend with health endpoint, empty tables, and smoke tests.

## Phase 1 – Transaction Ingestion Loop
- Implement `POST /api/receipts/ingest` and storage of ingestion metadata.
- Build `POST /api/transactions` with category/tag handling and linkage to receipts.
- Provide AI parsing pipeline for photo and voice/chat inputs (placeholder model ok).
- Deliverable: agent or manual scripts can ingest real-world receipts and voice statements end-to-end.

## Phase 2 – Savings & Summaries
- Create savings snapshot persistence and retrieval endpoints.
- Implement `GET /api/transactions/summary` with weekly, monthly, quarterly, yearly aggregations.
- Add forecasting stubs and budget comparison logic with configurable thresholds.
- Deliverable: dashboards or reports show spending trends plus savings progress.

## Phase 3 – AI Assistance & Automation
- Replace placeholder AI client with production integration (OpenAI or alternative).
- Enable proactive reminders after salary events and when savings updates are overdue.
- Generate natural-language explanations for summaries and planning suggestions.
- Deliverable: agents provide actionable insights and reminders with minimal human prompts.

## Phase 4 – Extensions & Scale
- Introduce multi-currency support, shared family accounts, and export capabilities.
- Harden security posture (encryption, retention policies, role-based access).
- Expand forecasting with anomaly detection and seasonal adjustments.
- Deliverable: feature-complete platform ready for broader user testing.

## Milestone Tracking
- Review milestones at the end of each phase with a short retro.
- Maintain a rolling backlog in `docs/requirements-and-tasks.md`.
- Keep agents aligned by updating this plan as scope evolves.
