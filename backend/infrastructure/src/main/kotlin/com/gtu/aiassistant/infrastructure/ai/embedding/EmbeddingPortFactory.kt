package com.gtu.aiassistant.infrastructure.ai.embedding

import io.ktor.client.HttpClient

object EmbeddingPortFactory {
    fun create(config: EmbeddingConfig, httpClient: HttpClient): EmbeddingPort =
        when (config.mode) {
            EmbeddingMode.HASH -> HashingEmbeddingPort(config.dimensions)
            EmbeddingMode.OPENAI -> OpenAIEmbeddingPort(config, httpClient)
        }
}
