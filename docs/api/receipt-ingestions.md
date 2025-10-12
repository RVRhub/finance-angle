# Receipt Ingestion API Quick Test

Use these cURL snippets to exercise the receipt ingestion endpoints while the app runs locally on the default port (8080).

## Create / Ingest Receipt Metadata

```bash
curl -X POST "http://localhost:8080/api/receipts/ingest" \
  -H "Content-Type: application/json" \
  -d '{
    "externalId": "sample-receipt-001",
    "receiptUri": "https://example.com/receipts/001.pdf",
    "metadata": "{\"uploadedBy\":\"qa-user\"}",
    "status": "PENDING"
  }'
```

- `externalId` must be unique and non-empty.
- `status` defaults to `PENDING`; valid values are defined in `ReceiptIngestionStatus`.
- `metadata` is an optional JSON string that will be stored in the `jsonb` column.

## Fetch Ingestion Status by External ID

```bash
curl "http://localhost:8080/api/receipts/sample-receipt-001"
```

Successful responses include the persisted ingestion record. A `404` indicates no ingestion with that `externalId` exists.
