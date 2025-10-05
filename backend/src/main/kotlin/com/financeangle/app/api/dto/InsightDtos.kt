package com.financeangle.app.api.dto

import com.financeangle.app.service.model.SummaryPeriod

data class InsightRecommendationResponse(
    val period: SummaryPeriod,
    val recommendations: List<String>
)
