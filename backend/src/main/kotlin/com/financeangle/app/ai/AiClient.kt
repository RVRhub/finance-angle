package com.financeangle.app.ai

import com.financeangle.app.service.model.InsightContext

interface AiClient {
    fun generateRecommendations(context: InsightContext): List<String>
}
