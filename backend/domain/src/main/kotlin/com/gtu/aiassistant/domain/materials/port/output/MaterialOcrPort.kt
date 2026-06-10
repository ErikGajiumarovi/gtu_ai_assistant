package com.gtu.aiassistant.domain.materials.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError

fun interface MaterialOcrPort {
    suspend operator fun invoke(command: MaterialOcrCommand): Either<InfrastructureError, MaterialOcrResult>
}

data class MaterialOcrCommand(
    val imageBytes: ByteArray,
    val imageContentType: String,
    val pageNumber: Int
)

data class MaterialOcrResult(
    val text: String,
    val averageConfidence: Double?
)
