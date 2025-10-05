package com.financeangle.app.api.dto

import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant

data class SavingsSnapshotRequest(
    @field:NotNull
    val amount: BigDecimal?,
    val capturedAt: Instant? = null,
    val notes: String? = null
)

data class SavingsSnapshotResponse(
    val id: Long,
    val amount: BigDecimal,
    val capturedAt: Instant,
    val notes: String?,
    val createdAt: Instant
)
