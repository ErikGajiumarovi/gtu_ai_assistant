package com.gtu.aiassistant.domain.materials.port.input

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.model.MaterialDocument
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

fun interface DownloadMaterialUseCase {
    suspend operator fun invoke(query: DownloadMaterialQuery): Either<DownloadMaterialError, DownloadMaterialResult>
}

data class DownloadMaterialQuery(
    val ownerUserId: UserId,
    val documentId: MaterialDocumentId
)

data class DownloadMaterialResult(
    val document: MaterialDocument,
    val bytes: ByteArray
)

sealed interface DownloadMaterialError {
    data object DocumentNotFound : DownloadMaterialError

    data class PersistenceFailed(
        val reason: InfrastructureError
    ) : DownloadMaterialError

    data class StorageFailed(
        val reason: InfrastructureError
    ) : DownloadMaterialError
}
