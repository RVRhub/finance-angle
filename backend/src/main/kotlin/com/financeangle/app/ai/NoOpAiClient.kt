package com.financeangle.app.ai

import com.financeangle.app.service.model.InsightContext

class NoOpAiClient : AiClient {
    override fun generateRecommendations(context: InsightContext): List<String> {
        val summary = context.summary
        return listOf(
            "AI integration not configured. Add your OpenAI credentials to unlock personalized guidance.",
            "Recent period total: ${'$'}{summary.totalAmount} across ${summary.totalsByCategory.size} categories.",
            context.latestSavingsAmount?.let { "Latest savings snapshot: ${'$'}it." }
                ?: "Capture a savings snapshot to start tracking your safety buffer."
        ).filterNotNull()
    }
}
