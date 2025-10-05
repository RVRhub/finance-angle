package com.financeangle.app.api.dto

import com.financeangle.app.domain.TransactionCategory
import com.financeangle.app.domain.TransactionSourceType
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant

data class TransactionRequest(
    @field:NotNull
    val amount: BigDecimal?,
    val category: TransactionCategory = TransactionCategory.OTHER,
    val occurredAt: Instant? = null,
    val notes: String? = null,
    val sourceType: TransactionSourceType? = null,
    val receiptReference: String? = null
)

data class TransactionResponse(
    val id: Long,
    val amount: BigDecimal,
    val category: TransactionCategory,
    val occurredAt: Instant,
    val notes: String?,
    val sourceType: TransactionSourceType,
    val receiptReference: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
