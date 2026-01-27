package com.financeangle.dashboard.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

data class TransactionRequest(
    @field:NotNull
    val date: LocalDate,
    @field:NotBlank
    @field:Size(max = 512)
    val description: String,
    val category: String? = null,
    @field:NotNull
    val amount: BigDecimal,
    val account: String? = null
)

data class SnapshotRequest(
    @field:NotNull
    val date: LocalDate,
    @field:NotNull
    val balance: BigDecimal,
    val note: String? = null,
    val account: String? = null
)

data class SummaryPoint(
    val month: YearMonth,
    val category: String?,
    val total: BigDecimal
)

data class ImportResult(
    val imported: Int,
    val skipped: Int,
    val errors: List<String>
)
