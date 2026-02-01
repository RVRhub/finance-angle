package com.financeangle.app.service

import com.financeangle.app.ai.AiClient
import com.financeangle.app.service.model.InsightContext
import com.financeangle.app.service.model.InsightRecommendationResult
import com.financeangle.app.service.model.SummaryPeriod
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InsightService(
    private val transactionService: TransactionService,
    private val savingsService: SavingsService,
    private val aiClient: AiClient
) {

    private val logger = LoggerFactory.getLogger(InsightService::class.java)

    @Transactional(readOnly = true)
    fun generateRecommendations(period: SummaryPeriod): InsightRecommendationResult {
        logger.info("Generating recommendations for {}", period)
        val summary = transactionService.generateSummary(period)
        val latestSavings = savingsService.fetchLatestSnapshot()?.amount
        logger.debug("Latest savings amount={}", latestSavings)
        val context = InsightContext(summary, latestSavings)
        val recommendations = aiClient.generateRecommendations(context)
        return InsightRecommendationResult(recommendations)
    }
}
