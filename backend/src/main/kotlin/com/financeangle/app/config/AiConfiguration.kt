package com.financeangle.app.config

import com.financeangle.app.ai.AiClient
import com.financeangle.app.ai.NoOpAiClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AiConfiguration {

    @Bean
    fun aiClient(): AiClient = NoOpAiClient()
}
