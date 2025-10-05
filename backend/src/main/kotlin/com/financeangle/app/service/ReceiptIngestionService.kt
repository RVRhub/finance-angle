package com.financeangle.app.service

import com.financeangle.app.domain.ReceiptIngestionEntity
import com.financeangle.app.repository.ReceiptIngestionRepository
import com.financeangle.app.service.model.ReceiptIngestionCommand
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReceiptIngestionService(
    private val receiptIngestionRepository: ReceiptIngestionRepository
) {

    @Transactional
    fun recordIngestion(command: ReceiptIngestionCommand): ReceiptIngestionEntity {
        val entity = receiptIngestionRepository.findByExternalId(command.externalId)
            .map { existing ->
                existing.receiptUri = command.receiptUri
                existing.metadata = command.metadata
                existing.status = command.status
                existing
            }
            .orElseGet {
                ReceiptIngestionEntity(
                    externalId = command.externalId,
                    receiptUri = command.receiptUri,
                    metadata = command.metadata,
                    status = command.status
                )
            }
        return receiptIngestionRepository.save(entity)
    }

    @Transactional(readOnly = true)
    fun fetchByExternalId(externalId: String): ReceiptIngestionEntity? {
        return receiptIngestionRepository.findByExternalId(externalId).orElse(null)
    }
}
