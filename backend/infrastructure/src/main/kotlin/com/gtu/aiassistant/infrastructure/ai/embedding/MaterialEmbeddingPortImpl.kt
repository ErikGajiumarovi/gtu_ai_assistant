package com.gtu.aiassistant.infrastructure.ai.embedding

import com.gtu.aiassistant.domain.materials.port.output.MaterialEmbeddingInput
import com.gtu.aiassistant.domain.materials.port.output.MaterialEmbeddingPort

class MaterialEmbeddingPortImpl(
    private val embeddingPort: EmbeddingPort
) : MaterialEmbeddingPort {
    override suspend fun invoke(input: MaterialEmbeddingInput) =
        embeddingPort(input.text)
}
