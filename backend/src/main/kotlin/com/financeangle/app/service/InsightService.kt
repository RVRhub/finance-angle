package com.financeangle.app.service

import com.financeangle.app.ai.AiClient
import com.financeangle.app.service.model.InsightContext
import com.financeangle.app.service.model.InsightRecommendationResult
import com.financeangle.app.service.model.SummaryPeriod
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InsightService(
    private val transactionService: TransactionService,
    private val savingsService: SavingsService,
    private val aiClient: AiClient
) {

    @Transactional(readOnly = true)
    fun generateRecommendations(period: SummaryPeriod): InsightRecommendationResult {
        val summary = transactionService.generateSummary(period)
        val latestSavings = savingsService.fetchLatestSnapshot()?.amount
        val context = InsightContext(summary, latestSavings)
        val recommendations = aiClient.generateRecommendations(context)
        return InsightRecommendationResult(recommendations)
    }
}
