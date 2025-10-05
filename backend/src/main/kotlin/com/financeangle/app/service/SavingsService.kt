package com.financeangle.app.service

import com.financeangle.app.domain.SavingsSnapshotEntity
import com.financeangle.app.repository.SavingsSnapshotRepository
import com.financeangle.app.service.model.SavingsSnapshotCommand
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SavingsService(
    private val savingsSnapshotRepository: SavingsSnapshotRepository
) {

    @Transactional
    fun recordSnapshot(command: SavingsSnapshotCommand): SavingsSnapshotEntity {
        val entity = SavingsSnapshotEntity(
            amount = command.amount,
            capturedAt = command.capturedAt,
            notes = command.notes
        )
        return savingsSnapshotRepository.save(entity)
    }

    @Transactional(readOnly = true)
    fun fetchLatestSnapshot(): SavingsSnapshotEntity? {
        return savingsSnapshotRepository.findTopByOrderByCapturedAtDesc().orElse(null)
    }
}
