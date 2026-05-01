package com.gtu.aiassistant.infrastructure.ai

data class AiConfig(
    val apiKey: String,
    val baseUrl: String,
    val model: String
) {
    companion object {
        const val DEFAULT_OLLAMA_API_KEY: String =
            "13f2adf1c3e44faaad76b22ad93665d6.Gbyrbwz_iRoa2Jv6-R19zQi5"

        const val DEFAULT_OLLAMA_OPENAI_BASE_URL: String = "https://ollama.com"

        const val GPT_OSS_20B: String = "gpt-oss:20b"
        const val GPT_OSS_120B: String = "gpt-oss:120b"

        fun default20b(): AiConfig =
            AiConfig(
                apiKey = DEFAULT_OLLAMA_API_KEY,
                baseUrl = DEFAULT_OLLAMA_OPENAI_BASE_URL,
                model = GPT_OSS_20B
            )

        fun default120b(): AiConfig =
            AiConfig(
                apiKey = DEFAULT_OLLAMA_API_KEY,
                baseUrl = DEFAULT_OLLAMA_OPENAI_BASE_URL,
                model = GPT_OSS_120B
            )
    }
}
