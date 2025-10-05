package com.financeangle.app.api

import com.financeangle.app.api.dto.InsightRecommendationResponse
import com.financeangle.app.service.InsightService
import com.financeangle.app.service.model.SummaryPeriod
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/insights")
@Validated
class InsightController(
    private val insightService: InsightService
) {

    @GetMapping("/recommendations")
    fun recommendations(@RequestParam(defaultValue = "MONTH") period: SummaryPeriod): InsightRecommendationResponse {
        val result = insightService.generateRecommendations(period)
        return InsightRecommendationResponse(period = period, recommendations = result.recommendations)
    }
}
