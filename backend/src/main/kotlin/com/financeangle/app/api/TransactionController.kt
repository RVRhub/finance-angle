package com.financeangle.app.api

import com.financeangle.app.api.dto.CategoryTotalResponse
import com.financeangle.app.api.dto.TransactionRequest
import com.financeangle.app.api.dto.TransactionResponse
import com.financeangle.app.api.dto.TransactionSummaryResponse
import com.financeangle.app.service.TransactionService
import com.financeangle.app.service.model.SummaryPeriod
import com.financeangle.app.service.model.TransactionCommand
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/transactions")
@Validated
class TransactionController(
    private val transactionService: TransactionService
) {

    @PostMapping
    fun createTransaction(@Valid @RequestBody request: TransactionRequest): TransactionResponse {
        val amount = request.amount ?: throw IllegalArgumentException("Amount is required")
        val occurredAt = request.occurredAt ?: Instant.now()
        val command = TransactionCommand(
            amount = amount,
            category = request.category,
            occurredAt = occurredAt,
            notes = request.notes,
            sourceType = request.sourceType ?: request.sourceTypeGuessed(),
            receiptReference = request.receiptReference
        )
        val saved = transactionService.recordTransaction(command)
        return TransactionResponse(
            id = saved.id,
            amount = saved.amount,
            category = saved.category,
            occurredAt = saved.occurredAt,
            notes = saved.notes,
            sourceType = saved.sourceType,
            receiptReference = saved.receiptReference,
            createdAt = saved.createdAt,
            updatedAt = saved.updatedAt
        )
    }

    @GetMapping("/summary")
    fun summarize(
        @RequestParam(defaultValue = "MONTH") period: SummaryPeriod,
        @RequestParam(required = false) reference: Instant?
    ): TransactionSummaryResponse {
        val summary = transactionService.generateSummary(period, reference ?: Instant.now())
        val categoryTotals = summary.totalsByCategory
            .entries
            .sortedByDescending { it.value }
            .map { CategoryTotalResponse(it.key, it.value) }
        return TransactionSummaryResponse(
            period = period,
            periodStart = summary.periodStart,
            periodEnd = summary.periodEnd,
            totalAmount = summary.totalAmount,
            categoryTotals = categoryTotals
        )
    }

    private fun TransactionRequest.sourceTypeGuessed() = when {
        receiptReference != null -> com.financeangle.app.domain.TransactionSourceType.PHOTO
        else -> com.financeangle.app.domain.TransactionSourceType.MANUAL
    }
}
