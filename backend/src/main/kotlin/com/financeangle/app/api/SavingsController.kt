package com.financeangle.app.api

import com.financeangle.app.api.dto.SavingsSnapshotRequest
import com.financeangle.app.api.dto.SavingsSnapshotResponse
import com.financeangle.app.service.SavingsService
import com.financeangle.app.service.model.SavingsSnapshotCommand
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/savings")
@Validated
class SavingsController(
    private val savingsService: SavingsService
) {

    @PostMapping("/snapshots")
    fun createSnapshot(@Valid @RequestBody request: SavingsSnapshotRequest): SavingsSnapshotResponse {
        val amount = request.amount ?: throw IllegalArgumentException("Amount is required")
        val command = SavingsSnapshotCommand(
            amount = amount,
            capturedAt = request.capturedAt ?: Instant.now(),
            notes = request.notes
        )
        val saved = savingsService.recordSnapshot(command)
        return SavingsSnapshotResponse(
            id = saved.id,
            amount = saved.amount,
            capturedAt = saved.capturedAt,
            notes = saved.notes,
            createdAt = saved.createdAt
        )
    }

    @GetMapping("/snapshots/latest")
    fun latestSnapshot(): ResponseEntity<SavingsSnapshotResponse> {
        val latest = savingsService.fetchLatestSnapshot()
            ?: return ResponseEntity.notFound().build()
        val response = SavingsSnapshotResponse(
            id = latest.id,
            amount = latest.amount,
            capturedAt = latest.capturedAt,
            notes = latest.notes,
            createdAt = latest.createdAt
        )
        return ResponseEntity.ok(response)
    }
}
