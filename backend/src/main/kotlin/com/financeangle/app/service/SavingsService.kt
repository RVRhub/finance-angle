package com.financeangle.app.service

import com.financeangle.app.domain.SavingsSnapshotEntity
import com.financeangle.app.repository.SavingsSnapshotRepository
import com.financeangle.app.service.model.SavingsSnapshotCommand
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SavingsService(
    private val savingsSnapshotRepository: SavingsSnapshotRepository
) {

    private val logger = LoggerFactory.getLogger(SavingsService::class.java)

    @Transactional
    fun recordSnapshot(command: SavingsSnapshotCommand): SavingsSnapshotEntity {
        logger.info("Recording savings snapshot amount={} capturedAt={}", command.amount, command.capturedAt)
        val entity = SavingsSnapshotEntity(
            amount = command.amount,
            capturedAt = command.capturedAt,
            notes = command.notes
        )
        val saved = savingsSnapshotRepository.save(entity)
        logger.debug("Recorded savings snapshot id={}", saved.id)
        return saved
    }

    @Transactional(readOnly = true)
    fun fetchLatestSnapshot(): SavingsSnapshotEntity? {
        logger.info("Fetching latest savings snapshot")
        return savingsSnapshotRepository.findTopByOrderByCapturedAtDesc().orElse(null)
    }
}
