package com.financeangle.app.api

import com.financeangle.app.api.dto.ReceiptIngestionRequest
import com.financeangle.app.api.dto.ReceiptIngestionResponse
import com.financeangle.app.service.ReceiptIngestionService
import com.financeangle.app.service.model.ReceiptIngestionCommand
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/receipts")
@Validated
class ReceiptIngestionController(
    private val receiptIngestionService: ReceiptIngestionService
) {

    @PostMapping("/ingest")
    fun ingest(@Valid @RequestBody request: ReceiptIngestionRequest): ReceiptIngestionResponse {
        val externalId = request.externalId ?: throw IllegalArgumentException("externalId is required")
        val command = ReceiptIngestionCommand(
            externalId = externalId,
            receiptUri = request.receiptUri,
            metadata = request.metadata,
            status = request.status
        )
        val saved = receiptIngestionService.recordIngestion(command)
        return ReceiptIngestionResponse(
            id = saved.id,
            externalId = saved.externalId,
            receiptUri = saved.receiptUri,
            status = saved.status,
            metadata = saved.metadata
        )
    }

    @GetMapping("/{externalId}")
    fun status(@PathVariable externalId: String): ResponseEntity<ReceiptIngestionResponse> {
        val ingestion = receiptIngestionService.fetchByExternalId(externalId)
            ?: return ResponseEntity.notFound().build()
        val response = ReceiptIngestionResponse(
            id = ingestion.id,
            externalId = ingestion.externalId,
            receiptUri = ingestion.receiptUri,
            status = ingestion.status,
            metadata = ingestion.metadata
        )
        return ResponseEntity.ok(response)
    }
}
