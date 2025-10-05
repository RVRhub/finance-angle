package com.financeangle.app.repository

import com.financeangle.app.domain.SavingsSnapshotEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface SavingsSnapshotRepository : JpaRepository<SavingsSnapshotEntity, Long> {
    fun findTopByOrderByCapturedAtDesc(): Optional<SavingsSnapshotEntity>
}
