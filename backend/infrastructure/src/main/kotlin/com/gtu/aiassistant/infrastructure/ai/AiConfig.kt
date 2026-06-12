package com.gtu.aiassistant.infrastructure.ai

import java.util.concurrent.atomic.AtomicInteger

data class AiConfig(
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val apiKeys: List<String> = listOf(apiKey)
) {
    fun normalizedApiKeys(): List<String> =
        apiKeys
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { listOf(apiKey) }

    companion object {
        const val DEFAULT_OLLAMA_API_KEY: String =
            "13f2adf1c3e44faaad76b22ad93665d6.Gbyrbwz_iRoa2Jv6-R19zQi5"

        const val DEFAULT_OLLAMA_OPENAI_BASE_URL: String = "https://ollama.com"

        const val GPT_OSS_20B: String = "gpt-oss:20b"
        const val GPT_OSS_120B: String = "gpt-oss:120b"
        const val GEMMA3_4B: String = "gemma3:4b"
        const val GEMMA4_31B: String = "gemma4:31b"

        fun default20b(): AiConfig =
            AiConfig(
                apiKey = DEFAULT_OLLAMA_API_KEY,
                baseUrl = DEFAULT_OLLAMA_OPENAI_BASE_URL,
                model = GEMMA4_31B
            )

        fun default120b(): AiConfig =
            AiConfig(
                apiKey = DEFAULT_OLLAMA_API_KEY,
                baseUrl = DEFAULT_OLLAMA_OPENAI_BASE_URL,
                model = GPT_OSS_120B
            )
    }
}

data class SelectedAiApiKey(
    val index: Int,
    val value: String
)

class AiApiKeySelector(apiKeys: List<String>) {
    private val normalizedApiKeys = apiKeys
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .ifEmpty { error("At least one AI API key must be configured") }
    private val nextIndex = AtomicInteger(0)

    fun next(): SelectedAiApiKey {
        val index = Math.floorMod(nextIndex.getAndIncrement(), normalizedApiKeys.size)
        return SelectedAiApiKey(index = index, value = normalizedApiKeys[index])
    }

    fun size(): Int = normalizedApiKeys.size
}
