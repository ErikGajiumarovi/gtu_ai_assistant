package com.gtu.aiassistant.domain.materials.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.model.MaterialChunk
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

fun interface SaveMaterialChunksPort {
    suspend operator fun invoke(chunks: List<MaterialChunk>): Either<InfrastructureError, Unit>
}

fun interface ReplaceMaterialDocumentChunksPort {
    suspend operator fun invoke(ownerUserId: UserId, documentId: MaterialDocumentId, chunks: List<MaterialChunk>): Either<InfrastructureError, Unit>
}

fun interface DeleteMaterialChunksPort {
    suspend operator fun invoke(ownerUserId: UserId, documentId: MaterialDocumentId): Either<InfrastructureError, Unit>
}
