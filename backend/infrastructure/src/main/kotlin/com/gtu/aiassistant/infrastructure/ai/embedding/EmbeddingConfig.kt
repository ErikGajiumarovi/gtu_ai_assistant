package com.gtu.aiassistant.infrastructure.ai.embedding

data class EmbeddingConfig(
    val mode: EmbeddingMode,
    val apiKey: String?,
    val baseUrl: String,
    val model: String,
    val dimensions: Int
)

enum class EmbeddingMode {
    HASH,
    OPENAI;

    companion object {
        fun from(raw: String?): EmbeddingMode =
            when (raw?.lowercase()) {
                "openai" -> OPENAI
                else -> HASH
            }
    }
}
