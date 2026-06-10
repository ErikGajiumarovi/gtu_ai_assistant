package com.gtu.aiassistant.domain.materials.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError

fun interface MaterialEmbeddingPort {
    suspend operator fun invoke(input: MaterialEmbeddingInput): Either<InfrastructureError, List<Float>>
}

data class MaterialEmbeddingInput(
    val text: String
)
