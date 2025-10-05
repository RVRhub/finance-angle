package com.financeangle.app.api.dto

import com.financeangle.app.domain.TransactionCategory
import com.financeangle.app.service.model.SummaryPeriod
import java.math.BigDecimal
import java.time.Instant

data class TransactionSummaryResponse(
    val period: SummaryPeriod,
    val periodStart: Instant,
    val periodEnd: Instant,
    val totalAmount: BigDecimal,
    val categoryTotals: List<CategoryTotalResponse>
)

data class CategoryTotalResponse(
    val category: TransactionCategory,
    val amount: BigDecimal
)
