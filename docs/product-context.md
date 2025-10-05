# Finance Angle Context Notes

## Goal
Create a lightweight personal finance tracker that captures day-to-day spending, snapshots of savings, and AI-assisted summaries so users can understand cash flow and plan ahead.

## Ingestion Channels
- **Photo receipts**: Uploading a supermarket bill or similar image should automatically create a transaction record with amount, date, merchant, and extracted line items when possible.
- **Voice or chat entries**: Example input: "Today I bought 2.5 kg potatoes at the market for 10 euro." The AI should parse the text/voice transcript into a structured transaction with category and notes.
- **Manual entries**: Permit direct form input inside the app for quick corrections or additions.

Each ingestion should produce a transaction record and store the supporting artifact (photo, transcript) on the user’s device/phone. The backend only needs the metadata stating that the upload happened.

## Transaction Model
- Capture amount, currency (default EUR, but support others later), date/time, merchant/source, category (food, entertainment, family, etc.), and optional notes.
- Allow attaching multiple tags so reports can group by family vs. personal, recurring vs. one-off, etc.
- Support linking to a receipt ingestion record for traceability.

## Savings Tracking
- Users log salary or transfers into savings wallets at least once per month.
- Store periodic "real savings" snapshots with timestamp, account label, and balance to show asset growth.

## Insights & Summaries
- Summaries by category for weekly, monthly, quarterly, and yearly periods.
- Highlight budget adherence, forecast next month’s spending based on trends, and suggest planned expenses.
- Provide running totals of savings vs. targets and alert if savings deposits are overdue.

## AI Assistance
1. Parse unstructured inputs (photo, voice, chat) into structured transactions automatically.
2. Summarize spending per category and timeframe with optional natural-language explanations.
3. Recommend savings allocations right after salary events and remind users to confirm real balances once or twice per month.

## Future Extensions
- Multi-currency handling with automatic conversion.
- Shared family accounts with collaborative budgeting.
- Export to CSV or accounting tools.
- Deeper prediction insights (seasonal adjustments, anomaly detection).

## Open Questions
- How long to retain raw artifacts (photos/audio) server-side, if ever?
- Should categories be user-defined or locked to a preset?
- What security requirements exist for storing financial notes and artifacts?
- What UI surfaces will invoke the AI flows (mobile app, chat interface, integrations)?
