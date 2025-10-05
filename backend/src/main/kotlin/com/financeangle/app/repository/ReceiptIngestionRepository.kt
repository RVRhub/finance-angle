package com.financeangle.app.repository

import com.financeangle.app.domain.ReceiptIngestionEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ReceiptIngestionRepository : JpaRepository<ReceiptIngestionEntity, Long> {
    fun findByExternalId(externalId: String): Optional<ReceiptIngestionEntity>
}
