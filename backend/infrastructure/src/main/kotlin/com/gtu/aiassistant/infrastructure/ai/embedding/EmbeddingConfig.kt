package com.gtu.aiassistant.infrastructure.ai.embedding

data class EmbeddingConfig(
    val mode: EmbeddingMode,
    val apiKey: String?,
    val baseUrl: String,
    val model: String,
    val dimensions: Int
) {
    fun profileFingerprint(): String =
        "${mode.name.lowercase()}:${baseUrl.trim().trimEnd('/')}:${model.trim()}:${dimensions}"
}

enum class EmbeddingMode {
    HASH,
    OPENAI,
    OLLAMA;

    companion object {
        fun from(raw: String?): EmbeddingMode =
            when (raw?.lowercase()) {
                "openai" -> OPENAI
                "ollama" -> OLLAMA
                else -> HASH
            }
    }
}
