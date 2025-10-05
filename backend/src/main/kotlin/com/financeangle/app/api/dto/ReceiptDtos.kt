package com.financeangle.app.api.dto

import com.financeangle.app.domain.ReceiptIngestionStatus
import jakarta.validation.constraints.NotBlank


data class ReceiptIngestionRequest(
    @field:NotBlank
    val externalId: String?,
    val receiptUri: String? = null,
    val metadata: String? = null,
    val status: ReceiptIngestionStatus = ReceiptIngestionStatus.PENDING
)

data class ReceiptIngestionResponse(
    val id: Long,
    val externalId: String,
    val receiptUri: String?,
    val status: ReceiptIngestionStatus,
    val metadata: String?
)
