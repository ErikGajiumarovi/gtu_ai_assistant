package com.gtu.aiassistant.infrastructure.ai.embedding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EmbeddingConfigTest {
    @Test
    fun `embedding mode supports ollama`() {
        assertEquals(EmbeddingMode.OLLAMA, EmbeddingMode.from("ollama"))
        assertEquals(EmbeddingMode.OLLAMA, EmbeddingMode.from("OLLAMA"))
    }

    @Test
    fun `embedding profile fingerprint includes mode endpoint model and dimensions`() {
        val config = EmbeddingConfig(
            mode = EmbeddingMode.OLLAMA,
            apiKey = null,
            baseUrl = " http://ollama:11434/ ",
            model = "bge-m3",
            dimensions = 1024
        )

        assertEquals("ollama:http://ollama:11434:bge-m3:1024", config.profileFingerprint())
    }
}

class OllamaEmbeddingPortTest {
    @Test
    fun `ollama embedding parser reads first embedding`() {
        val embedding = parseOllamaEmbeddingResponse(
            response = """{"model":"bge-m3","embeddings":[[0.1,0.2,0.3]],"total_duration":123}""",
            expectedDimensions = 3
        )

        assertEquals(listOf(0.1f, 0.2f, 0.3f), embedding)
    }

    @Test
    fun `ollama embedding parser rejects dimension mismatch`() {
        assertFailsWith<IllegalArgumentException> {
            parseOllamaEmbeddingResponse(
                response = """{"embeddings":[[0.1,0.2]]}""",
                expectedDimensions = 3
            )
        }
    }
}
