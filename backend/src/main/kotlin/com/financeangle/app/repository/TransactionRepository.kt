package com.financeangle.app.repository

import com.financeangle.app.domain.TransactionEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface TransactionRepository : JpaRepository<TransactionEntity, Long> {
    fun findAllByOccurredAtBetween(start: Instant, end: Instant): List<TransactionEntity>
}
