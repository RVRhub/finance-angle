package com.financeangle.app.service

import com.financeangle.app.domain.ReceiptIngestionEntity
import com.financeangle.app.repository.ReceiptIngestionRepository
import com.financeangle.app.service.model.ReceiptIngestionCommand
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReceiptIngestionService(
    private val receiptIngestionRepository: ReceiptIngestionRepository
) {

    private val logger = LoggerFactory.getLogger(ReceiptIngestionService::class.java)

    @Transactional
    fun recordIngestion(command: ReceiptIngestionCommand): ReceiptIngestionEntity {
        logger.info(
            "Recording receipt ingestion externalId={} status={}",
            command.externalId,
            command.status
        )
        val entity = receiptIngestionRepository.findByExternalId(command.externalId)
            .map { existing ->
                logger.debug("Updating existing ingestion externalId={}", command.externalId)
                existing.receiptUri = command.receiptUri
                existing.metadata = command.metadata
                existing.status = command.status
                existing
            }
            .orElseGet {
                logger.debug("Creating new ingestion externalId={}", command.externalId)
                ReceiptIngestionEntity(
                    externalId = command.externalId,
                    receiptUri = command.receiptUri,
                    metadata = command.metadata,
                    status = command.status
                )
            }
        val saved = receiptIngestionRepository.save(entity)
        logger.debug("Saved ingestion id={} externalId={}", saved.id, saved.externalId)
        return saved
    }

    @Transactional(readOnly = true)
    fun fetchByExternalId(externalId: String): ReceiptIngestionEntity? {
        logger.info("Fetching receipt ingestion externalId={}", externalId)
        return receiptIngestionRepository.findByExternalId(externalId).orElse(null)
    }
}
