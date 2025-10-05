package com.financeangle.app.service.model

import com.financeangle.app.domain.ReceiptIngestionStatus
import com.financeangle.app.domain.TransactionCategory
import com.financeangle.app.domain.TransactionSourceType
import java.math.BigDecimal
import java.time.Instant

data class TransactionCommand(
    val amount: BigDecimal,
    val category: TransactionCategory,
    val occurredAt: Instant,
    val notes: String?,
    val sourceType: TransactionSourceType,
    val receiptReference: String?
)

data class TransactionSummary(
    val periodStart: Instant,
    val periodEnd: Instant,
    val totalAmount: BigDecimal,
    val totalsByCategory: Map<TransactionCategory, BigDecimal>
)

enum class SummaryPeriod {
    WEEK,
    MONTH,
    QUARTER,
    YEAR
}

data class SavingsSnapshotCommand(
    val amount: BigDecimal,
    val capturedAt: Instant,
    val notes: String?
)

data class ReceiptIngestionCommand(
    val externalId: String,
    val receiptUri: String?,
    val metadata: String?,
    val status: ReceiptIngestionStatus
)

data class InsightContext(
    val summary: TransactionSummary,
    val latestSavingsAmount: BigDecimal?
)

data class InsightRecommendationResult(
    val recommendations: List<String>
)
