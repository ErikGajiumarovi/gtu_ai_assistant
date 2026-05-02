package com.gtu.aiassistant.infrastructure.ai.embedding

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError

fun interface EmbeddingPort {
    suspend operator fun invoke(text: String): Either<InfrastructureError, List<Float>>
}
